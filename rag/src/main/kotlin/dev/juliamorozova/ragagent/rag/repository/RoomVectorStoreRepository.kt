package dev.juliamorozova.ragagent.rag.repository

import dev.juliamorozova.ragagent.rag.local.db.ChunkDao
import dev.juliamorozova.ragagent.rag.local.mapper.toEntity
import dev.juliamorozova.ragagent.rag.local.mapper.toRetrievedChunk
import dev.juliamorozova.ragagent.domain.model.Chunk
import dev.juliamorozova.ragagent.domain.model.EmbeddingVector
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
import dev.juliamorozova.ragagent.domain.repository.VectorStoreRepository
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * Room-backed [VectorStoreRepository] with brute-force cosine similarity computed in
 * Kotlin — the "simple option" from the project's technical spec. Fine for a
 * portfolio corpus; see README "Known trade-offs" for the trade-off vs. an
 * embedded/backend vector DB.
 */
class RoomVectorStoreRepository @Inject constructor(
    private val chunkDao: ChunkDao,
) : VectorStoreRepository {

    override suspend fun store(chunk: Chunk, embedding: EmbeddingVector) {
        chunkDao.insert(chunk.toEntity(embedding))
    }

    override suspend fun findSimilar(queryEmbedding: EmbeddingVector, topK: Int): List<RetrievedChunk> {
        return chunkDao.getAll()
            .map { entity -> entity.toRetrievedChunk(cosineSimilarity(entity.embedding, queryEmbedding.values)) }
            .sortedByDescending { it.score }
            .take(topK)
    }

    override suspend fun clear() {
        chunkDao.clear()
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding dimension mismatch: ${a.size} vs ${b.size}" }
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
