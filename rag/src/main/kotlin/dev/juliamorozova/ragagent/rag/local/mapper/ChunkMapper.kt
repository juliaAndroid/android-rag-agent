package dev.juliamorozova.ragagent.rag.local.mapper

import dev.juliamorozova.ragagent.rag.local.db.ChunkEntity
import dev.juliamorozova.ragagent.domain.model.Chunk
import dev.juliamorozova.ragagent.domain.model.EmbeddingVector
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk

fun ChunkEntity.toDomain(): Chunk = Chunk(
    id = id,
    documentId = documentId,
    text = text,
    position = position,
    createdAtMillis = createdAtMillis,
)

fun Chunk.toEntity(embedding: EmbeddingVector): ChunkEntity = ChunkEntity(
    id = id,
    documentId = documentId,
    text = text,
    position = position,
    embedding = embedding.values,
    createdAtMillis = createdAtMillis,
)

fun ChunkEntity.toRetrievedChunk(score: Float): RetrievedChunk =
    RetrievedChunk(chunk = toDomain(), score = score)
