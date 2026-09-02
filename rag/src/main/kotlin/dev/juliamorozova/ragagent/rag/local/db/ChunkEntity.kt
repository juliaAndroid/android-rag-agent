package dev.juliamorozova.ragagent.rag.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Room-persisted chunk + its embedding vector.
 *
 * The vector is stored as a converted BLOB and similarity is computed in Kotlin (see
 * [dev.juliamorozova.ragagent.rag.repository.RoomVectorStoreRepository]), not in SQL —
 * the simple option for a portfolio-scale corpus. Revisit if/when this moves to an
 * embedded or backend vector DB.
 */
@Entity(tableName = "chunks")
@TypeConverters(FloatArrayConverter::class)
data class ChunkEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    val text: String,
    val position: Int,
    @ColumnInfo(name = "embedding") val embedding: FloatArray,
) {
    // FloatArray needs a structural equals/hashCode for Room's change detection and tests.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChunkEntity) return false
        return id == other.id &&
            documentId == other.documentId &&
            text == other.text &&
            position == other.position &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + position
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

class FloatArrayConverter {
    @TypeConverter
    fun fromFloatArray(value: FloatArray): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(value.size * Float.SIZE_BYTES)
        value.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(bytes: ByteArray): FloatArray {
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        val floats = FloatArray(bytes.size / Float.SIZE_BYTES)
        for (i in floats.indices) floats[i] = buffer.float
        return floats
    }
}
