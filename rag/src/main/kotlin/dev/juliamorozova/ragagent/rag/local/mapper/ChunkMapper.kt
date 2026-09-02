package dev.juliamorozova.ragagent.rag.local.mapper

import dev.juliamorozova.ragagent.rag.local.db.ChunkEntity
import dev.juliamorozova.ragagent.domain.model.Chunk
import dev.juliamorozova.ragagent.domain.model.EmbeddingVector
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk

/**
 * Explicit domain <-> persistence mappers. Kept as free functions in `rag` (not
 * extension functions on domain types) so the domain module stays free of any
 * knowledge that Room exists.
 */
fun ChunkEntity.toDomain(): Chunk = Chunk(
    id = id,
    documentId = documentId,
    text = text,
    position = position,
)

fun Chunk.toEntity(embedding: EmbeddingVector): ChunkEntity = ChunkEntity(
    id = id,
    documentId = documentId,
    text = text,
    position = position,
    embedding = embedding.values,
)

fun ChunkEntity.toRetrievedChunk(score: Float): RetrievedChunk =
    RetrievedChunk(chunk = toDomain(), score = score)
