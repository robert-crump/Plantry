package com.example.plantry.data.claude

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.example.plantry.data.settings.ScanModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Why a call to Claude failed, coarse enough to show a German message for each. */
enum class ClaudeFailure {
    INVALID_KEY,
    NO_CREDIT,
    PERMISSION_DENIED,
    MODEL_NOT_FOUND,
    RATE_LIMITED,
    OVERLOADED,
    NETWORK,
    UNKNOWN,

    // Only when scanning a recipe.
    NO_API_KEY,
    /** Claude declined to answer (stop reason "refusal"). */
    REFUSED,
    /** The answer was cut off at the output limit (stop reason "max_tokens"). */
    TRUNCATED,
    /** The answer could not be parsed. */
    BAD_RESPONSE,
    /** Claude found no recipe on the photo. */
    NOT_A_RECIPE;

    /** The key itself won't work, as opposed to a passing problem like a missing connection. */
    val isKeyProblem: Boolean get() = this == INVALID_KEY || this == NO_CREDIT || this == PERMISSION_DENIED

    companion object {
        /** Prefers the API's error type (e.g. "billing_error") and falls back to the HTTP status. */
        fun fromError(status: Int, type: String?): ClaudeFailure = when (type) {
            "authentication_error" -> INVALID_KEY
            "billing_error" -> NO_CREDIT
            "permission_error" -> PERMISSION_DENIED
            "not_found_error" -> MODEL_NOT_FOUND
            "rate_limit_error" -> RATE_LIMITED
            "overloaded_error", "api_error", "timeout_error" -> OVERLOADED
            else -> fromStatus(status)
        }

        /** Maps an HTTP status from the Claude API (see docs: errors) to a failure. */
        fun fromStatus(status: Int): ClaudeFailure = when (status) {
            401 -> INVALID_KEY
            402 -> NO_CREDIT
            403 -> PERMISSION_DENIED
            404 -> MODEL_NOT_FOUND
            429 -> RATE_LIMITED
            500, 529 -> OVERLOADED
            else -> if (status >= 500) OVERLOADED else UNKNOWN
        }
    }
}

sealed interface ConnectionResult {
    data object Success : ConnectionResult
    data class Failure(val reason: ClaudeFailure) : ConnectionResult
}

fun interface ConnectionTester {
    suspend fun test(apiKey: String, model: ScanModel): ConnectionResult
}

/** Makes the smallest possible Messages call with the given key and model. */
class AnthropicConnectionTester(private val models: ModelCatalog = ModelCatalog.shared) : ConnectionTester {

    override suspend fun test(apiKey: String, model: ScanModel): ConnectionResult {
        val params = MessageCreateParams.builder()
            .model(withContext(Dispatchers.IO) { models.resolve(apiKey, model).id })
            .maxTokens(16L)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .addUserMessage("Ping")
            .build()
        return when (val answer = callClaude(apiKey, params, maxRetries = 0)) {
            is ClaudeResult.Success -> ConnectionResult.Success
            is ClaudeResult.Failure -> ConnectionResult.Failure(answer.reason)
        }
    }
}

sealed interface ClaudeResult<out T> {
    data class Success<T>(val value: T) : ClaudeResult<T>
    data class Failure(val reason: ClaudeFailure) : ClaudeResult<Nothing>
}

/** Decodes Claude's JSON [text] as [T]; a missing or malformed answer is [ClaudeFailure.BAD_RESPONSE]. */
internal inline fun <reified T> Json.decodeAnswer(text: String?): ClaudeResult<T> = try {
    ClaudeResult.Success(decodeFromString<T>(text ?: return ClaudeResult.Failure(ClaudeFailure.BAD_RESPONSE)))
} catch (_: SerializationException) {
    ClaudeResult.Failure(ClaudeFailure.BAD_RESPONSE)
} catch (_: IllegalArgumentException) {
    ClaudeResult.Failure(ClaudeFailure.BAD_RESPONSE)
}

/** Makes [params]' call and returns the answer's text, or why there is none. */
suspend fun callClaude(apiKey: String, params: MessageCreateParams, maxRetries: Int? = null): ClaudeResult<String?> =
    withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).apply { maxRetries?.let(::maxRetries) }.build()
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
