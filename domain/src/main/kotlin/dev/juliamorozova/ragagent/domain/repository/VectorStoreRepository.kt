package dev.juliamorozova.ragagent.domain.repository

import dev.juliamorozova.ragagent.domain.model.Chunk
import dev.juliamorozova.ragagent.domain.model.EmbeddingVector
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk

/**
 * Storage + similarity search over chunk embeddings.
 *
 * Week 1 implementation: Room/SQLite with brute-force cosine similarity (see plan).
 * Swappable later for an embedded or backend vector DB (stretch goal) without the
 * domain or presentation layers changing.
 */
interface VectorStoreRepository {
    suspend fun store(chunk: Chunk, embedding: EmbeddingVector)

    suspend fun findSimilar(queryEmbedding: EmbeddingVector, topK: Int): List<RetrievedChunk>

    suspend fun clear()
}
