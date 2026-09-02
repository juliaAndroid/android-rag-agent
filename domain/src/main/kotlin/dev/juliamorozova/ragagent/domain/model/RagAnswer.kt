package dev.juliamorozova.ragagent.domain.model

/**
 * The final answer shown to the user: generated text grounded in the chunks that were
 * retrieved for the query, so the UI can render sources/citations alongside it.
 */
data class RagAnswer(
    val text: String,
    val citations: List<RetrievedChunk>,
)
