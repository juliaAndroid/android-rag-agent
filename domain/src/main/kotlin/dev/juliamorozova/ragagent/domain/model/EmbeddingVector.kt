package dev.juliamorozova.ragagent.domain.model

/**
 * A dense vector representation of a [Chunk] (or a query string), produced by the
 * embeddings provider decided in the project plan (Voyage AI to start; on-device is
 * the stretch goal).
 */
data class EmbeddingVector(
    val values: FloatArray,
    val dimension: Int = values.size,
) {
    init {
        require(values.isNotEmpty()) { "EmbeddingVector must not be empty" }
        require(values.size == dimension) { "dimension must match values.size" }
    }

    // data class does not generate a value-based equals/hashCode for FloatArray content,
    // so both are implemented explicitly to keep this type safe to use in tests/collections.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingVector) return false
        return values.contentEquals(other.values)
    }

    override fun hashCode(): Int = values.contentHashCode()
}
