package com.example.plantry.data.claude

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.example.plantry.data.Ingredient
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import com.example.plantry.data.settings.ScanModel
import com.example.plantry.data.usda.UsdaFood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * A food that is not in the user's ingredient table yet. Its [id] is negative: a temporary id that
 * recipe lines refer to until the ingredient is created on save.
 */
data class NewFood(
    val id: Long,
    /** German name, e.g. "Räuchertofu". */
    val name: String,
    /** The recipe line it came from, for context; may be blank. */
    val originalText: String,
    /** English terms for searching the USDA data; empty until Claude provided them. */
    val searchTerms: List<String>,
)

/** A [NewFood] with the USDA entries the local search found for its [NewFood.searchTerms]. */
data class FoodCandidates(val food: NewFood, val candidates: List<UsdaFood>)

/** Where a new ingredient's nutrition comes from: a USDA entry or the values on the package. */
sealed interface NutritionSource {
    /** Per 100 g. */
    val nutrition: Nutrition

    data class Usda(val food: UsdaFood) : NutritionSource {
        override val nutrition: Nutrition get() = food.nutrition
    }

    /** Typed in from the package label; the ingredient gets no USDA reference. */
    data class Label(override val nutrition: Nutrition) : NutritionSource
}

/** Claude's validated proposal for a new ingredient. */
data class IngredientProposal(
    val name: String,
    /** Null when no USDA candidate fits, so the user must pick one or enter the label values. */
    val source: NutritionSource?,
    val searchTerms: List<String>,
    val storeSection: StoreSection,
    val plantPoints: PlantPoints,
) {
    /** The USDA entry, if the nutrition comes from one. */
    val food: UsdaFood? get() = (source as? NutritionSource.Usda)?.food

    /** The unreviewed ingredient to create. Requires [source]. */
    fun toIngredient(id: Long = 0): Ingredient {
        val source = checkNotNull(source) { "a new ingredient needs a nutrition source" }
        return Ingredient(
            id = id,
            name = name,
            fdcId = food?.fdcId,
            usdaDescription = food?.description,
            nutrition = source.nutrition,
            storeSection = storeSection,
            plantPoints = plantPoints,
            reviewed = false,
        )
    }
}

sealed interface ClaudeResult<out T> {
    data class Success<T>(val value: T) : ClaudeResult<T>
    data class Failure(val reason: ClaudeFailure) : ClaudeResult<Nothing>
}

interface IngredientProposer {
    /** English USDA search terms per [NewFood.id]; a small text-only call. */
    suspend fun searchTerms(apiKey: String, model: ScanModel, foods: List<NewFood>): ClaudeResult<Map<Long, List<String>>>

    /** Picks a USDA entry from each food's candidates and proposes the remaining attributes, per [NewFood.id]. */
    suspend fun propose(
        apiKey: String,
        model: ScanModel,
        foods: List<FoodCandidates>,
    ): ClaudeResult<Map<Long, IngredientProposal>>
}

class AnthropicIngredientProposer : IngredientProposer {

    override suspend fun searchTerms(
        apiKey: String,
        model: ScanModel,
        foods: List<NewFood>,
    ): ClaudeResult<Map<Long, List<String>>> {
        val params = request(
            model,
            OutputConfig.Effort.LOW,
            ProposalPrompt.SEARCH_TERMS_SYSTEM,
            ProposalPrompt.searchTermsSchema(),
            ProposalPrompt.searchTermsMessage(foods),
        )
        return when (val answer = call(apiKey, params)) {
            is ClaudeResult.Success -> ProposalParser.searchTerms(answer.value, foods)
            is ClaudeResult.Failure -> answer
        }
    }

    override suspend fun propose(
        apiKey: String,
        model: ScanModel,
        foods: List<FoodCandidates>,
    ): ClaudeResult<Map<Long, IngredientProposal>> {
        val params = request(
            model,
            OutputConfig.Effort.MEDIUM,
            ProposalPrompt.PROPOSE_SYSTEM,
            ProposalPrompt.proposeSchema(),
            ProposalPrompt.proposeMessage(foods),
        )
        return when (val answer = call(apiKey, params)) {
            is ClaudeResult.Success -> ProposalParser.proposals(answer.value, foods)
            is ClaudeResult.Failure -> answer
        }
    }

    private fun request(
        model: ScanModel,
        effort: OutputConfig.Effort,
        system: String,
        schema: JsonOutputFormat.Schema,
        message: String,
    ) = MessageCreateParams.builder()
        .model(model.modelId)
        .maxTokens(8_000L)
        .system(system)
        .outputConfig(
            OutputConfig.builder()
                .effort(effort)
                .format(JsonOutputFormat.builder().schema(schema).build())
                .build(),
        )
        .addUserMessage(message)
        .build()

    /** The answer's text, or why there is none. */
    private suspend fun call(apiKey: String, params: MessageCreateParams): ClaudeResult<String?> =
        withContext(Dispatchers.IO) {
            val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
            try {
                val response = client.messages().create(params)
                when (response.stopReason().orElse(null)) {
                    StopReason.REFUSAL -> ClaudeResult.Failure(ClaudeFailure.REFUSED)
                    StopReason.MAX_TOKENS -> ClaudeResult.Failure(ClaudeFailure.TRUNCATED)
                    else -> ClaudeResult.Success(
                        response.content().mapNotNull { block -> block.text().orElse(null)?.text() }.lastOrNull(),
                    )
                }
            } catch (e: AnthropicServiceException) {
                ClaudeResult.Failure(ClaudeFailure.fromError(e.statusCode(), e.errorType().map { it.asString() }.orElse(null)))
            } catch (_: AnthropicIoException) {
                ClaudeResult.Failure(ClaudeFailure.NETWORK)
            } finally {
                client.close()
            }
        }
}

object ProposalPrompt {

    val SEARCH_TERMS_SYSTEM = """
        You help a German meal-planning app find foods in the USDA FoodData Central SR Legacy
        database, which has English descriptions such as "Tofu, raw, firm" or "Onions, raw".
        For each German food name, give 1 to 3 English search phrases, from specific to general,
        each 1 to 3 words that USDA descriptions use, e.g. "Räuchertofu" -> ["tofu smoked", "tofu"],
        "Kichererbsen aus der Dose" -> ["chickpeas canned", "chickpeas"]. Return one entry per
        food with the key as given.
    """.trimIndent() + "\n" + MatchingRules.SEARCH_FORMS

    /** How the scan asks for search terms of unmatched lines, so no extra call is needed. */
    val SEARCH_TERMS_RULE =
        "English search phrases for the USDA SR Legacy database, from specific to general, " +
            "1 to 3 words each, e.g. [\"tofu smoked\", \"tofu\"]; " +
            MatchingRules.SEARCH_FORMS.replace('\n', ' ').removeSuffix(".").replaceFirstChar { it.lowercase() }

    fun searchTermsMessage(foods: List<NewFood>) = buildString {
        appendLine("Foods (key, German name, recipe line):")
        foods.forEach { appendLine("${key(it.id)}\t${it.name}\t${it.originalText}") }
    }

    val PROPOSE_SYSTEM = buildString {
        appendLine(
            """
            You help a German meal-planning app add new foods to the user's ingredient table. For
            each new food you get its German name, the recipe line it came from, and candidate
            entries of the USDA FoodData Central SR Legacy database found by a local search.

            For each food return:
            - key: as given.
            - fdcId: the candidate that best represents the food as used in the recipe, following
              the USDA rules below. 0 if no candidate is the same food; do not pick a different
              food just because it is similar.
            - name: see the name rule below.
            - storeSection, plantPoints: see the rules below.
            """.trimIndent(),
        )
        appendLine()
        appendLine(MatchingRules.USDA_ENTRY)
        appendLine()
        appendRule("Name", MatchingRules.NAME)
        appendRule("storeSection", MatchingRules.STORE_SECTION)
        append("plantPoints: ${MatchingRules.PLANT_POINTS}")
    }

    private fun StringBuilder.appendRule(title: String, rule: String) {
        appendLine("$title: $rule")
        appendLine()
    }

    fun proposeMessage(foods: List<FoodCandidates>) = buildString {
        foods.forEach { (food, candidates) ->
            appendLine("key ${key(food.id)}: ${food.name}")
            if (food.originalText.isNotBlank()) appendLine("recipe line: ${food.originalText}")
            if (candidates.isEmpty()) {
                appendLine("candidates: none")
            } else {
                appendLine("candidates (fdcId, tab, description):")
                candidates.forEach { appendLine("${it.fdcId}\t${it.description}") }
            }
            appendLine()
        }
    }

    /** "N1" for the temporary id -1, so Claude never confuses new foods with table ids. */
    fun key(id: Long) = "N${-id}"

    fun searchTermsSchema(): JsonOutputFormat.Schema = rootSchema(
        "foods" to arraySchema(
            objectSchema(
                "key" to mapOf("type" to "string"),
                "searchTerms" to arraySchema(mapOf("type" to "string")),
            ),
        ),
    )

    fun proposeSchema(): JsonOutputFormat.Schema = rootSchema(
        "ingredients" to arraySchema(
            objectSchema(
                "key" to mapOf("type" to "string"),
                "fdcId" to mapOf("type" to "integer"),
                "name" to mapOf("type" to "string"),
                "storeSection" to enumSchema(StoreSection.entries),
                "plantPoints" to enumSchema(PlantPoints.entries),
            ),
        ),
    )

    private fun enumSchema(values: List<Enum<*>>) = mapOf("type" to "string", "enum" to values.map { it.name })

    private fun arraySchema(items: Map<String, Any>) = mapOf("type" to "array", "items" to items)

    /** An object with all [properties] required and no others, as structured outputs need. */
    private fun objectSchema(vararg properties: Pair<String, Map<String, Any>>): Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to properties.toMap(),
        "required" to properties.map { it.first },
        "additionalProperties" to false,
    )

    private fun rootSchema(vararg properties: Pair<String, Map<String, Any>>): JsonOutputFormat.Schema =
        JsonOutputFormat.Schema.builder()
            .apply { objectSchema(*properties).forEach { (name, value) -> putAdditionalProperty(name, JsonValue.from(value)) } }
            .build()
}

/** Turns Claude's JSON answers into validated proposals; pure, so it is unit-tested. */
object ProposalParser {

    @Serializable
    private data class SearchTermsJson(val foods: List<FoodTermsJson>)

    @Serializable
    private data class FoodTermsJson(val key: String, val searchTerms: List<String>)

    @Serializable
    private data class ProposalsJson(val ingredients: List<ProposalJson>)

    @Serializable
    private data class ProposalJson(
        val key: String,
        val fdcId: Long = 0,
        val name: String = "",
        val storeSection: String = "",
        val plantPoints: String = "",
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Search terms per food id; foods Claude left out get none. */
    fun searchTerms(text: String?, foods: List<NewFood>): ClaudeResult<Map<Long, List<String>>> {
        val parsed = decode<SearchTermsJson>(text) ?: return ClaudeResult.Failure(ClaudeFailure.BAD_RESPONSE)
        val terms = parsed.foods.associate { it.key to cleanTerms(it.searchTerms) }
        return ClaudeResult.Success(foods.associate { it.id to terms[ProposalPrompt.key(it.id)].orEmpty() })
    }

    /**
     * One proposal per food. Invalid or missing fields fall back to neutral values the user reviews
     * later: a USDA entry that was not a candidate counts as none and unknown enums become
     * OTHER / ZERO. A food Claude left out gets only defaults.
     */
    fun proposals(text: String?, foods: List<FoodCandidates>): ClaudeResult<Map<Long, IngredientProposal>> {
        val parsed = decode<ProposalsJson>(text) ?: return ClaudeResult.Failure(ClaudeFailure.BAD_RESPONSE)
        val byKey = parsed.ingredients.associateBy { it.key.trim() }
        return ClaudeResult.Success(
            foods.associate { candidates ->
                val food = candidates.food
                val answer = byKey[ProposalPrompt.key(food.id)] ?: ProposalJson(key = ProposalPrompt.key(food.id))
                food.id to toProposal(answer, candidates)
            },
        )
    }

    private fun toProposal(answer: ProposalJson, candidates: FoodCandidates): IngredientProposal {
        val food = candidates.food
        return IngredientProposal(
            name = answer.name.trim().ifEmpty { food.name.trim() },
            source = candidates.candidates.firstOrNull { it.fdcId == answer.fdcId }?.let(NutritionSource::Usda),
            searchTerms = food.searchTerms,
            storeSection = enumOrNull<StoreSection>(answer.storeSection) ?: StoreSection.OTHER,
            plantPoints = enumOrNull<PlantPoints>(answer.plantPoints) ?: PlantPoints.ZERO,
        )
    }

    /** Trimmed, non-blank and distinct; Claude is asked for at most three. */
    fun cleanTerms(terms: List<String>) = terms.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(3)

    private inline fun <reified T> decode(text: String?): T? = try {
        text?.let { json.decodeFromString<T>(it) }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name == name.trim().uppercase() }
}
