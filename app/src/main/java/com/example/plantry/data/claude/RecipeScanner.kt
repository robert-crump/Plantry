package com.example.plantry.data.claude

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.example.plantry.data.Ingredient
import com.example.plantry.data.settings.ScanModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.Base64

/** One ingredient line as Claude read it from the photo. */
data class ScannedLine(
    val originalText: String,
    val grams: Double,
    /** Null when no ingredient of the user's table matches. */
    val ingredientId: Long?,
    /** Claude's German name for the food, e.g. "Petersilie". */
    val ingredientName: String,
    /** Hard to read, or a vague amount such as "1 Bund", so [grams] is a rough guess. */
    val uncertain: Boolean,
    /** English USDA search terms, only for lines without [ingredientId]. */
    val searchTerms: List<String> = emptyList(),
)

/** What Claude read from a cookbook page; unknown numbers are null. */
data class ScannedRecipe(
    val title: String,
    val servings: Int?,
    val cookingTimeMinutes: Int?,
    val page: Int?,
    val lines: List<ScannedLine>,
)

sealed interface ScanResult {
    data class Success(val recipe: ScannedRecipe) : ScanResult
    data class Failure(val reason: ClaudeFailure) : ScanResult
}

fun interface RecipeScanner {
    /** Reads [photo] (JPEG) in one call, matching lines against [ingredients]. */
    suspend fun scan(apiKey: String, model: ScanModel, photo: ByteArray, ingredients: List<Ingredient>): ScanResult
}

class AnthropicRecipeScanner : RecipeScanner {

    override suspend fun scan(
        apiKey: String,
        model: ScanModel,
        photo: ByteArray,
        ingredients: List<Ingredient>,
    ): ScanResult = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        try {
            val response = client.messages().create(request(model, photo, ingredients))
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

    private fun request(model: ScanModel, photo: ByteArray, ingredients: List<Ingredient>) =
        MessageCreateParams.builder()
            .model(model.modelId)
            .maxTokens(16_000L)
            .system(ScanPrompt.system(ingredients))
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.MEDIUM)
                    .format(JsonOutputFormat.builder().schema(ScanPrompt.schema()).build())
                    .build(),
            )
            .addUserMessageOfBlockParams(
                listOf(
                    ContentBlockParam.ofImage(
                        ImageBlockParam.builder()
                            .source(
                                Base64ImageSource.builder()
                                    .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                                    .data(Base64.getEncoder().encodeToString(photo))
                                    .build(),
                            )
                            .build(),
                    ),
                    ContentBlockParam.ofText("Lies dieses Rezept."),
                ),
            )
            // On a safety refusal, retry server-side on the model Anthropic recommends.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build()
}

object ScanPrompt {

    fun system(ingredients: List<Ingredient>): String = buildString {
        appendLine(
            """
            You read a photo of a cookbook page (usually German) for a meal-planning app and extract
            the recipe's metadata and its ingredient lines. Method steps are not needed: the user
            cooks from the book.

            - title: the recipe title as printed.
            - servings: the number of servings stated; for a range such as "2-4" the higher number;
              0 if none is stated.
            - cookingTimeMinutes: the total time stated, in minutes; 0 if none is stated.
            - page: the printed page number if visible; 0 otherwise.
            - lines: one entry per ingredient line, in order. Section headings such as
              "Für den Teig:" are not lines. If a line offers alternatives, use the first.
              - originalText: the line exactly as printed, with amount and unit,
                e.g. "1 Bund glatte Petersilie".
              - grams: your best estimate of the weight in grams of the amount as printed, for
                the whole recipe (not per serving). Use typical weights for pieces, bunches,
                cans and spoons.
              - ingredientId: the id of the matching entry in the user's ingredient table below,
                or 0 if none fits. Match the same food in an equivalent form (fresh vs. fresh,
                cooked vs. cooked); do not match a different food just because it is similar.
              - ingredientName: a short German name of the food, e.g. "Petersilie".
              - uncertain: true when the line was hard to read (handwriting, blur, cut off) or
                its amount is vague ("1 Bund", "etwas", "nach Geschmack", "1 Dose"), so the
                grams are a rough guess; false otherwise.
              - searchTerms: only when ingredientId is 0: 1 to 3 ${ProposalPrompt.SEARCH_TERMS_RULE}.
                Empty otherwise.

            If the photo shows no recipe, return an empty title and no lines.

            The user's ingredient table (id, tab, German name):
            """.trimIndent(),
        )
        ingredients.sortedBy { it.name.lowercase() }.forEach { appendLine("${it.id}\t${it.name}") }
    }

    fun schema(): JsonOutputFormat.Schema {
        val line = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "originalText" to mapOf("type" to "string"),
                "grams" to mapOf("type" to "number"),
                "ingredientId" to mapOf("type" to "integer"),
                "ingredientName" to mapOf("type" to "string"),
                "uncertain" to mapOf("type" to "boolean"),
                "searchTerms" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            ),
            "required" to listOf("originalText", "grams", "ingredientId", "ingredientName", "uncertain", "searchTerms"),
            "additionalProperties" to false,
        )
        val properties = mapOf(
            "title" to mapOf("type" to "string"),
            "servings" to mapOf("type" to "integer"),
            "cookingTimeMinutes" to mapOf("type" to "integer"),
            "page" to mapOf("type" to "integer"),
            "lines" to mapOf("type" to "array", "items" to line),
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
        val cookingTimeMinutes: Int,
        val page: Int,
        val lines: List<LineJson>,
    )

    @Serializable
    private data class LineJson(
        val originalText: String,
        val grams: Double,
        val ingredientId: Long,
        val ingredientName: String,
        val uncertain: Boolean,
        val searchTerms: List<String> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Ids not in [knownIngredientIds] count as "no match". A line without a positive weight is
     * marked uncertain, since its grams must be corrected.
     */
    fun toResult(text: String?, knownIngredientIds: Set<Long>): ScanResult {
        val parsed = try {
            json.decodeFromString<ScanJson>(text ?: return ScanResult.Failure(ClaudeFailure.BAD_RESPONSE))
        } catch (_: SerializationException) {
            return ScanResult.Failure(ClaudeFailure.BAD_RESPONSE)
        } catch (_: IllegalArgumentException) {
            return ScanResult.Failure(ClaudeFailure.BAD_RESPONSE)
        }
        if (parsed.title.isBlank() && parsed.lines.isEmpty()) return ScanResult.Failure(ClaudeFailure.NOT_A_RECIPE)
        return ScanResult.Success(
            ScannedRecipe(
                title = parsed.title.trim(),
                servings = parsed.servings.takeIf { it > 0 },
                cookingTimeMinutes = parsed.cookingTimeMinutes.takeIf { it > 0 },
                page = parsed.page.takeIf { it > 0 },
                lines = parsed.lines.filter { it.originalText.isNotBlank() }.map { line ->
                    val ingredientId = line.ingredientId.takeIf { it in knownIngredientIds }
                    ScannedLine(
                        originalText = line.originalText.trim(),
                        grams = line.grams.coerceAtLeast(0.0),
                        ingredientId = ingredientId,
                        ingredientName = line.ingredientName.trim(),
                        uncertain = line.uncertain || line.grams <= 0.0,
                        searchTerms = if (ingredientId == null) ProposalParser.cleanTerms(line.searchTerms) else emptyList(),
                    )
                },
            ),
        )
    }
}
