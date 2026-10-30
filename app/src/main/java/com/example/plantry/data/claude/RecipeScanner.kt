package com.example.plantry.data.claude

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.example.plantry.data.CookingSteps
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.LineUnit
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.settings.ScanModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import kotlin.math.roundToLong

/** One ingredient line as Claude read it from the photo. */
data class ScannedLine(
    val originalText: String,
    /** In [unit], for the whole recipe. */
    val amount: Double,
    /** g, ml or one of the [PieceUnit]s; a line that fits none ("1 Handvoll") comes as grams. */
    val unit: LineUnit,
    /** Null when no ingredient of the user's table matches. */
    val ingredientId: Long?,
    /** Claude's German name for the food, e.g. "Petersilie". */
    val ingredientName: String,
    /** Hard to read, or a vague amount such as "1 Handvoll", so [amount] is a rough guess. */
    val uncertain: Boolean,
    /** The line as printed, when it says more than amount, unit and food; null shows the generated text. */
    val wording: String? = null,
    /** Claude's grams for one [unit], for when the ingredient lacks it; null for g and ml. */
    val unitGrams: Double? = null,
    /** English USDA search terms, only for lines without [ingredientId]. */
    val searchTerms: List<String> = emptyList(),
)

/** What Claude read from a cookbook page; unknown numbers are null. */
data class ScannedRecipe(
    val title: String,
    val servings: Int?,
    /** "Gesamtzeit". */
    val totalTimeMinutes: Int?,
    val page: Int?,
    val lines: List<ScannedLine>,
    /** "Arbeitszeit". */
    val activeTimeMinutes: Int? = null,
    /** The cooking steps, condensed by [StepRules]; empty when the photo shows no method. */
    val steps: List<String> = emptyList(),
)

sealed interface ScanResult {
    data class Success(val recipe: ScannedRecipe) : ScanResult
    data class Failure(val reason: ClaudeFailure) : ScanResult
}

fun interface RecipeScanner {
    /** Reads [photo] (JPEG) in one call, matching lines against [ingredients], whose [units] are by ingredient id. */
    suspend fun scan(
        apiKey: String,
        model: ScanModel,
        photo: ByteArray,
        ingredients: List<Ingredient>,
        units: Map<Long, List<IngredientUnit>>,
    ): ScanResult
}

class AnthropicRecipeScanner(private val models: ModelCatalog = ModelCatalog.shared) : RecipeScanner {

    override suspend fun scan(
        apiKey: String,
        model: ScanModel,
        photo: ByteArray,
        ingredients: List<Ingredient>,
        units: Map<Long, List<IngredientUnit>>,
    ): ScanResult = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        try {
            val response = client.messages().create(request(models.resolve(apiKey, model).id, photo, ingredients, units))
            when (response.stopReason().orElse(null)) {
                StopReason.REFUSAL -> return@withContext ScanResult.Failure(ClaudeFailure.REFUSED)
                StopReason.MAX_TOKENS -> return@withContext ScanResult.Failure(ClaudeFailure.TRUNCATED)
                else -> Unit
            }
            // After a server-side fallback the answer is the last text block.
            val text = response.content().mapNotNull { block -> block.text().orElse(null)?.text() }.lastOrNull()
            ScanParser.toResult(text, ingredients.map { it.id }.toSet())
        } catch (e: AnthropicServiceException) {
            ScanResult.Failure(ClaudeFailure.fromError(e.statusCode(), e.errorType().map { it.asString() }.orElse(null)))
        } catch (_: AnthropicIoException) {
            ScanResult.Failure(ClaudeFailure.NETWORK)
        } finally {
            client.close()
        }
    }

    private fun request(modelId: String, photo: ByteArray, ingredients: List<Ingredient>, units: Map<Long, List<IngredientUnit>>) =
        MessageCreateParams.builder()
            .model(modelId)
            .maxTokens(16_000L)
            .system(ScanPrompt.system(ingredients, units))
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.MEDIUM)
                    .format(JsonOutputFormat.builder().schema(ScanPrompt.schema()).build())
                    .build(),
            )
            .addUserMessageOfBlockParams(
                listOf(
                    jpegBlock(photo),
                    ContentBlockParam.ofText("Lies dieses Rezept."),
                ),
            )
            // On a safety refusal, retry server-side on the model Anthropic recommends.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build()
}

object ScanPrompt {

    private const val GRAMS = "g"
    private const val MILLILITERS = "ml"

    fun system(ingredients: List<Ingredient>, units: Map<Long, List<IngredientUnit>> = emptyMap()): String = buildString {
        appendLine(
            """
            You read a photo of a cookbook page (usually German) for a meal-planning app and extract
            the recipe's metadata, its ingredient lines and its cooking steps.

            - title: the recipe title as printed.
            - servings: the number of servings stated; for a range such as "2-4" the higher number;
              0 if none is stated.
            - activeMinutes: the hands-on time stated, in minutes: "Arbeitszeit", "aktive Zeit", or
              "Zubereitung(szeit)" when printed next to a cooking, baking, resting or marinating
              time. 0 if none is stated. Never estimate it from the method.
            - totalMinutes: the time until the food is ready, in minutes: "Gesamtzeit" if printed,
              else the sum of all times printed (e.g. "Zubereitung 20 Min., Backzeit 45 Min." is
              65). A single unqualified time, also a lone "Zubereitungszeit", is the total time.
              0 if no time is stated.
            - page: the printed page number if visible; 0 otherwise.
            - lines: one entry per ingredient line, in order. Section headings such as
              "Für den Teig:" are not lines. If a line offers alternatives, use the first.
              - originalText: the line exactly as printed, with amount and unit,
                e.g. "1 Bund glatte Petersilie".
              - amount and unit: the amount as printed, for the whole recipe (not per serving),
                in one of the units ${UNIT_WORDS.joinToString(", ")}. Fractions as decimals
                ("½ TL" is 0.5 TL); for a range such as "2-3" the higher number. kg and l become
                g and ml (times 1000). A bare count ("2 Zwiebeln") is Stück, as is "Kopf" or
                "Knolle"; "Päckchen" is Packung, "Cup" is Tasse. An amount that fits none of
                these units ("1 Handvoll", "1 Prise", "etwas", "1 daumengroßes Stück") is your
                best estimate in g, with the printed line as wording.
              - wording: the line as printed when it says more than amount, unit and food name:
                an amount given in g instead of its own unit, a size ("1 große Zwiebel") or a
                preparation ("fein gehackt", "abgetropft"). Empty when the line reads just
                like amount, unit and food ("200 g rote Linsen", "2 EL Olivenöl").
              - unitGrams: only when unit is neither g nor ml and the matched ingredient below
                does not list that unit (or ingredientId is 0): your best estimate of the
                grams of one unit, using typical weights for pieces, bunches and spoons.
                Canned goods that are drained count in drained grams (about 240 g for
                "1 Dose Kichererbsen"). 0 otherwise.
              - ingredientId: the id of the matching entry in the user's ingredient table below,
                or 0 if none fits. Match the same food in an equivalent form (fresh vs. fresh,
                cooked vs. cooked); do not match a different food just because it is similar.
              - ingredientName: a short German name of the food, e.g. "Petersilie".
              - uncertain: true when the line was hard to read (handwriting, blur, cut off) or
                its amount is vague ("1 Handvoll", "etwas", "nach Geschmack"), so the amount is
                a rough guess; false otherwise.
              - searchTerms: only when ingredientId is 0: 1 to 3 ${ProposalPrompt.SEARCH_TERMS_RULE}.
                Empty otherwise.
            - steps: the cooking steps, following the step rules below. The photo is often only
              the ingredient page: if no method is visible, no steps.

            If the photo shows no recipe, return an empty title, no lines and no steps.
            """.trimIndent(),
        )
        appendLine()
        appendLine(StepRules.RULES)
        appendLine()
        appendLine(
            """
            The user's ingredient table (id, tab, German name; for ingredients counted in units a
            tab and the weight of one unit per unit, e.g. "1 Scheibe = 37.5 g"):
            """.trimIndent(),
        )
        ingredients.sortedBy { it.name.lowercase() }.forEach { ingredient ->
            val piece = units[ingredient.id].orEmpty().joinToString("") {
                "\t1 ${it.unit.singular} = ${formatGrams((it.gramsPerUnit * 10).roundToLong() / 10.0)} g"
            }
            appendLine("${ingredient.id}\t${ingredient.name}$piece")
        }
    }

    /** The words Claude answers a line's unit with: "g", "ml" and each [PieceUnit]'s singular. */
    val UNIT_WORDS: List<String> = listOf(GRAMS, MILLILITERS) + PieceUnit.entries.map { it.singular }

    /** The unit Claude answered with, or null for a word not in [UNIT_WORDS]. */
    fun unitOf(word: String): LineUnit? = when (word.trim().lowercase()) {
        GRAMS -> LineUnit.Grams
        MILLILITERS -> LineUnit.Milliliters
        else -> PieceUnit.entries.find { it.singular.equals(word.trim(), ignoreCase = true) }?.let(LineUnit::Of)
    }

    private fun formatGrams(grams: Double) = BigDecimal.valueOf(grams).stripTrailingZeros().toPlainString()

    fun schema(): JsonOutputFormat.Schema {
        val line = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "originalText" to mapOf("type" to "string"),
                "amount" to mapOf("type" to "number"),
                "unit" to mapOf("type" to "string", "enum" to UNIT_WORDS),
                "wording" to mapOf("type" to "string"),
                "unitGrams" to mapOf("type" to "number"),
                "ingredientId" to mapOf("type" to "integer"),
                "ingredientName" to mapOf("type" to "string"),
                "uncertain" to mapOf("type" to "boolean"),
                "searchTerms" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            ),
            "required" to listOf(
                "originalText", "amount", "unit", "wording", "unitGrams", "ingredientId", "ingredientName", "uncertain", "searchTerms",
            ),
            "additionalProperties" to false,
        )
        val properties = mapOf(
            "title" to mapOf("type" to "string"),
            "servings" to mapOf("type" to "integer"),
            "activeMinutes" to mapOf("type" to "integer"),
            "totalMinutes" to mapOf("type" to "integer"),
            "page" to mapOf("type" to "integer"),
            "lines" to mapOf("type" to "array", "items" to line),
            "steps" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
        )
        return JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(properties))
            .putAdditionalProperty("required", JsonValue.from(properties.keys.toList()))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }
}

/** Turns Claude's JSON answer into a [ScanResult]; pure, so it is unit-tested. */
object ScanParser {

    @Serializable
    private data class ScanJson(
        val title: String,
        val servings: Int,
        val activeMinutes: Int,
        val totalMinutes: Int,
        val page: Int,
        val lines: List<LineJson>,
        val steps: List<String> = emptyList(),
    )

    @Serializable
    private data class LineJson(
        val originalText: String,
        val amount: Double,
        val unit: String,
        val wording: String = "",
        val unitGrams: Double = 0.0,
        val ingredientId: Long,
        val ingredientName: String,
        val uncertain: Boolean,
        val searchTerms: List<String> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Steps are cleaned up as typed ones are ([CookingSteps.parse]). Ids not in [knownIngredientIds] count as "no match". A line without a positive amount, or in
     * a unit not in [ScanPrompt.UNIT_WORDS] (then read as grams), is marked uncertain, since its
     * amount must be corrected.
     */
    fun toResult(text: String?, knownIngredientIds: Set<Long>): ScanResult {
        val parsed = try {
            json.decodeFromString<ScanJson>(text ?: return ScanResult.Failure(ClaudeFailure.BAD_RESPONSE))
        } catch (_: SerializationException) {
            return ScanResult.Failure(ClaudeFailure.BAD_RESPONSE)
        } catch (_: IllegalArgumentException) {
            return ScanResult.Failure(ClaudeFailure.BAD_RESPONSE)
        }
        val steps = parsed.steps.flatMap(CookingSteps::parse)
        if (parsed.title.isBlank() && parsed.lines.isEmpty() && steps.isEmpty()) return ScanResult.Failure(ClaudeFailure.NOT_A_RECIPE)
        return ScanResult.Success(
            ScannedRecipe(
                title = parsed.title.trim(),
                servings = parsed.servings.takeIf { it > 0 },
                totalTimeMinutes = parsed.totalMinutes.takeIf { it > 0 },
                activeTimeMinutes = parsed.activeMinutes.takeIf { it > 0 },
                page = parsed.page.takeIf { it > 0 },
                lines = parsed.lines.filter { it.originalText.isNotBlank() }.map { line ->
                    val ingredientId = line.ingredientId.takeIf { it in knownIngredientIds }
                    val unit = ScanPrompt.unitOf(line.unit)
                    ScannedLine(
                        originalText = line.originalText.trim(),
                        amount = line.amount.coerceAtLeast(0.0),
                        unit = unit ?: LineUnit.Grams,
                        ingredientId = ingredientId,
                        ingredientName = line.ingredientName.trim(),
                        uncertain = line.uncertain || line.amount <= 0.0 || unit == null,
                        wording = line.wording.trim().ifEmpty { null },
                        unitGrams = line.unitGrams.takeIf { unit is LineUnit.Of && it > 0.0 },
                        searchTerms = if (ingredientId == null) ProposalParser.cleanTerms(line.searchTerms) else emptyList(),
                    )
                },
                steps = steps,
            ),
        )
    }
}
