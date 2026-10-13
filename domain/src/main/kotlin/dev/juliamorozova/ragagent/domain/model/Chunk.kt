package dev.juliamorozova.ragagent.domain.model

/** A retrievable unit from chunking. Framework-free: no Room annotations; `rag` maps it to its persistence model. */
data class Chunk(
    val id: String,
    val documentId: String,
    val text: String,
    val position: Int,
    val metadata: Map<String, String> = emptyMap(),
    val createdAtMillis: Long = System.currentTimeMillis(),
)
