package com.example.plantry.data.claude

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.models.ModelListParams
import com.example.plantry.data.settings.ScanModel
import java.time.Duration
import java.time.Instant

/** A concrete Claude model: the id for API calls and the name to show, e.g. "Opus 5.5". */
data class ResolvedModel(val id: String, val name: String)

/** One entry of the API's model list. */
data class ListedModel(val id: String, val displayName: String, val createdAt: Instant)

/**
 * Turns the tier the user picked into the newest model of that tier, as listed by the API, so the
 * app follows new releases without an update. The list is cached for [CACHE_TTL]; when it cannot
 * be fetched, the tier's [ScanModel.fallback] stands in.
 */
class ModelCatalog(
    private val fetch: (apiKey: String) -> List<ListedModel>,
    private val now: () -> Instant = Instant::now,
) {
    private var cached: Pair<Instant, Map<ScanModel, ResolvedModel>>? = null

    /** Blocking: call off the main thread. */
    @Synchronized
    fun resolve(apiKey: String, tier: ScanModel): ResolvedModel {
        cached?.takeIf { Duration.between(it.first, now()) < CACHE_TTL }?.let { return it.second.getValue(tier) }
        val listed = try {
            fetch(apiKey)
        } catch (_: Exception) {
            return peek(tier)
        }
        val resolved = ScanModel.entries.associateWith { newest(listed, it) ?: it.fallback }
        cached = now() to resolved
        return resolved.getValue(tier)
    }

    /** What is known without a request: the last resolution, else the fallback. */
    @Synchronized
    fun peek(tier: ScanModel): ResolvedModel = cached?.second?.get(tier) ?: tier.fallback

    private fun newest(listed: List<ListedModel>, tier: ScanModel): ResolvedModel? =
        listed.filter { it.id.startsWith("claude-") && "-${tier.family}-" in it.id }
            .maxByOrNull { it.createdAt }
            ?.let { ResolvedModel(it.id, it.displayName.removePrefix("Claude ").trim()) }

    companion object {
        val CACHE_TTL: Duration = Duration.ofHours(24)

        /** The one the app uses; shared so every Claude call and the settings see the same list. */
        val shared = ModelCatalog(::fetchFromApi)

        private fun fetchFromApi(apiKey: String): List<ListedModel> {
            val client = AnthropicOkHttpClient.builder().apiKey(apiKey).maxRetries(0).build()
            try {
                return client.models().list(ModelListParams.builder().limit(1000L).build()).data()
                    .map { ListedModel(it.id(), it.displayName(), it.createdAt().toInstant()) }
            } finally {
                client.close()
            }
        }
    }
}
