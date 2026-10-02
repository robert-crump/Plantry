package com.example.plantry.data.claude

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.example.plantry.data.settings.ScanModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
class AnthropicConnectionTester : ConnectionTester {

    override suspend fun test(apiKey: String, model: ScanModel): ConnectionResult = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).maxRetries(0).build()
        try {
            client.messages().create(
                MessageCreateParams.builder()
                    .model(model.modelId)
                    .maxTokens(16L)
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                    .addUserMessage("Ping")
                    .build(),
            )
            ConnectionResult.Success
        } catch (e: AnthropicServiceException) {
            ConnectionResult.Failure(
                ClaudeFailure.fromError(e.statusCode(), e.errorType().map { it.asString() }.orElse(null)),
            )
        } catch (_: AnthropicIoException) {
            ConnectionResult.Failure(ClaudeFailure.NETWORK)
        } finally {
            client.close()
        }
    }
}
