package dev.juliamorozova.ragagent.domain.model

/**
 * A [Chunk] returned by retrieval, together with its similarity score against the
 * query. Surfaced to the UI as a citation.
 */
data class RetrievedChunk(
    val chunk: Chunk,
    val score: Float,
)
