package dev.juliamorozova.ragagent.rag.embedding

import dev.juliamorozova.ragagent.domain.model.EmbeddingVector
import dev.juliamorozova.ragagent.domain.usecase.EmbedTextUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val MAX_RETRIES = 5
private const val INITIAL_BACKOFF_MS = 1_000L

// Voyage's free tier is ~3 requests/minute — a rate limit, not a total-count cap, so
// spacing calls at least this far apart avoids 429s almost entirely, rather than just
// reacting to them after the fact.
private const val MIN_INTERVAL_BETWEEN_REQUESTS_MS = 20_000L

/**
 * Calls the Voyage AI embeddings API (Anthropic's recommended embeddings provider
 * for RAG) to turn text into an [EmbeddingVector].
 *
 * Self-throttles to stay under the free tier's rate limit, and retries a 429 with
 * exponential backoff as a fallback if throttling alone wasn't enough (clock drift,
 * a limit tighter than documented, concurrent use of the same key elsewhere, etc.).
 */
@Singleton
class VoyageEmbedTextUseCase @Inject constructor(
    private val api: VoyageEmbeddingsApi,
) : EmbedTextUseCase {

    private val requestMutex = Mutex()
    private var lastRequestAtMs = 0L

    override suspend fun invoke(text: String): EmbeddingVector = invoke(listOf(text)).first()

    override suspend fun invoke(texts: List<String>): List<EmbeddingVector> {
        if (texts.isEmpty()) return emptyList()

        val response = embedWithRetry(texts)

        // Defensive: sort by the response's own index rather than assuming the API
        // returns embeddings in input order, so the zip against `texts`/`chunks` upstream
        // stays correct even if that assumption is ever wrong.
        return response.data
            .sortedBy { it.index }
            .map { EmbeddingVector(values = it.embedding.toFloatArray()) }
    }

    private suspend fun embedWithRetry(texts: List<String>): VoyageEmbeddingsResponse {
        var attempt = 0
        var backoffMillis = INITIAL_BACKOFF_MS
        while (true) {
            throttle()
            try {
                return api.embed(VoyageEmbeddingsRequest(input = texts))
            } catch (e: HttpException) {
                if (e.code() != HTTP_TOO_MANY_REQUESTS || attempt >= MAX_RETRIES) throw e
                attempt++
                delay(backoffMillis.milliseconds)
                backoffMillis *= 2
            }
        }
    }

    /** Blocks until at least [MIN_INTERVAL_BETWEEN_REQUESTS_MS] has passed since the
     * previous call — mutex-guarded because this class is a Hilt singleton and could,
     * in principle, be called concurrently from more than one coroutine. */
    private suspend fun throttle() {
        requestMutex.withLock {
            val waitMs = MIN_INTERVAL_BETWEEN_REQUESTS_MS - (System.currentTimeMillis() - lastRequestAtMs)
            if (waitMs > 0) delay(waitMs.milliseconds)
            lastRequestAtMs = System.currentTimeMillis()
        }
    }
}
