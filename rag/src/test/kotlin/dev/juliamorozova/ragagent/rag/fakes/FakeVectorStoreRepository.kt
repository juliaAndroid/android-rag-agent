package dev.juliamorozova.ragagent.rag.fakes

import dev.juliamorozova.ragagent.domain.model.Chunk
import dev.juliamorozova.ragagent.domain.model.EmbeddingVector
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
import dev.juliamorozova.ragagent.domain.repository.VectorStoreRepository
import kotlin.math.sqrt

/**
 * In-memory [VectorStoreRepository] for tests. [failOnStore] is one-shot: when set to
 * true, only the *next* [store] call throws (and resets the flag), so a test can force
 * exactly one chunk in a batch to fail without needing per-chunk targeting.
 */
class FakeVectorStoreRepository(
    var failOnStore: Boolean = false,
) : VectorStoreRepository {

    private val stored = mutableListOf<Pair<Chunk, EmbeddingVector>>()

    override suspend fun store(chunk: Chunk, embedding: EmbeddingVector) {
        if (failOnStore) {
            failOnStore = false
            error("Forced store failure")
        }
        stored += chunk to embedding
    }

    override suspend fun findSimilar(queryEmbedding: EmbeddingVector, topK: Int): List<RetrievedChunk> {
        return stored
            .map { (chunk, embedding) -> RetrievedChunk(chunk, cosineSimilarity(embedding.values, queryEmbedding.values)) }
            .sortedByDescending { it.score }
            .take(topK)
    }

    override suspend fun clear() {
        stored.clear()
    }

    override suspend fun count(): Int = stored.size

    override suspend fun getAll(): List<Chunk> = stored.map { it.first }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }
}
