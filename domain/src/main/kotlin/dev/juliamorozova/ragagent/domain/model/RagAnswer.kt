package dev.juliamorozova.ragagent.domain.model

data class RagAnswer(
    val text: String,
    val citations: List<RetrievedChunk>,
)
