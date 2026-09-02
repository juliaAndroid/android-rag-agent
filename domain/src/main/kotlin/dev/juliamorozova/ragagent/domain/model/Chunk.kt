package dev.juliamorozova.ragagent.domain.model

/**
 * A single retrievable unit produced by the chunking step of the RAG pipeline.
 *
 * Kept deliberately framework-free: no Room annotations here. The `data` module maps
 * this to/from its own persistence model so the domain layer never depends on Android.
 */
data class Chunk(
    val id: String,
    val documentId: String,
    val text: String,
    /** Order of this chunk within its source document, for citation display. */
    val position: Int,
    val metadata: Map<String, String> = emptyMap(),
)
