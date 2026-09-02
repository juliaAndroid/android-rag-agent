package dev.juliamorozova.ragagent.domain.model

data class IngestResult(
    val total: Int,
    val succeeded: Int,
    val failures: List<IngestionFailure>,
)

data class IngestionFailure(
    val chunkId: String,
    val reason: String,
)