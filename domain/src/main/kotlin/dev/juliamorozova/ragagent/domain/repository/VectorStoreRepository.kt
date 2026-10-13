package dev.juliamorozova.ragagent.domain.repository

import dev.juliamorozova.ragagent.domain.model.Chunk
import dev.juliamorozova.ragagent.domain.model.EmbeddingVector
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk

/**
 * Storage and similarity search over chunk embeddings; Room with brute-force cosine now, swappable for a vector
 * DB.
 */
interface VectorStoreRepository {
    suspend fun store(chunk: Chunk, embedding: EmbeddingVector)

    suspend fun findSimilar(queryEmbedding: EmbeddingVector, topK: Int): List<RetrievedChunk>

    suspend fun clear()

    suspend fun count(): Int

    suspend fun getAll(): List<Chunk>
}
