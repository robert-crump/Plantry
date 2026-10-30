package com.example.plantry.data.claude

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.example.plantry.data.CookingSteps
import com.example.plantry.data.settings.ScanModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

fun interface StepsCondenser {
    /** Kürzen: [text], the Zubereitung as typed, as concise steps by [StepRules]; empty if it holds no method. */
    suspend fun condense(apiKey: String, model: ScanModel, text: String): ClaudeResult<List<String>>
}

class AnthropicStepsCondenser(private val models: ModelCatalog = ModelCatalog.shared) : StepsCondenser {

    override suspend fun condense(apiKey: String, model: ScanModel, text: String): ClaudeResult<List<String>> {
        val modelId = withContext(Dispatchers.IO) { models.resolve(apiKey, model).id }
        val params = MessageCreateParams.builder()
            .model(modelId)
            .maxTokens(8_000L)
            .system(StepsPrompt.CONDENSE_SYSTEM)
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.MEDIUM)
                    .format(JsonOutputFormat.builder().schema(StepsPrompt.schema()).build())
                    .build(),
            )
            .addUserMessage(StepsPrompt.condenseMessage(text))
            .build()
        return when (val answer = callClaude(apiKey, params)) {
            is ClaudeResult.Success -> StepsParser.steps(answer.value)
            is ClaudeResult.Failure -> answer
        }
    }
}

object StepsPrompt {

    val CONDENSE_SYSTEM = """
        You rewrite the cooking steps of a recipe for a meal-planning app. The user gives the method
        as text: read from a cookbook, pasted, or written by hand. Return it as steps following the
        rules below.
    """.trimIndent() + "\n\n" + StepRules.RULES

    fun condenseMessage(text: String) = "Zubereitung:\n${text.trim()}"

    /** `{"steps": [string]}`; an empty list when there is no method. */
    fun schema(): JsonOutputFormat.Schema = JsonOutputFormat.Schema.builder()
        .putAdditionalProperty("type", JsonValue.from("object"))
        .putAdditionalProperty(
            "properties",
            JsonValue.from(mapOf("steps" to mapOf("type" to "array", "items" to mapOf("type" to "string")))),
        )
        .putAdditionalProperty("required", JsonValue.from(listOf("steps")))
        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
        .build()
}

/** Turns Claude's JSON answer into steps; pure, so it is unit-tested. */
object StepsParser {

    @Serializable
    private data class StepsJson(val steps: List<String>)

    private val json = Json { ignoreUnknownKeys = true }

    /** Cleaned up as typed steps are ([CookingSteps.parse]), so a step never carries a number or a line break. */
    fun steps(text: String?): ClaudeResult<List<String>> {
        val parsed = try {
            json.decodeFromString<StepsJson>(text ?: return ClaudeResult.Failure(ClaudeFailure.BAD_RESPONSE))
        } catch (_: SerializationException) {
            return ClaudeResult.Failure(ClaudeFailure.BAD_RESPONSE)
        } catch (_: IllegalArgumentException) {
            return ClaudeResult.Failure(ClaudeFailure.BAD_RESPONSE)
        }
        return ClaudeResult.Success(parsed.steps.flatMap(CookingSteps::parse))
    }
}
