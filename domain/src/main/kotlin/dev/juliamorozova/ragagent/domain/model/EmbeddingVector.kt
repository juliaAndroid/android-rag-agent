package dev.juliamorozova.ragagent.domain.model

data class EmbeddingVector(
    val values: FloatArray,
    val dimension: Int = values.size,
) {
    init {
        require(values.isNotEmpty()) { "EmbeddingVector must not be empty" }
        require(values.size == dimension) { "dimension must match values.size" }
    }

    // data class has no value-based equals/hashCode for FloatArray, so both are written explicitly.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingVector) return false
        return values.contentEquals(other.values)
    }

    override fun hashCode(): Int = values.contentHashCode()
}
