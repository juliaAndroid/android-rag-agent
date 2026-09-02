package dev.juliamorozova.ragagent.rag.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ChunkEntity::class], version = 1, exportSchema = true)
abstract class RagDatabase : RoomDatabase() {
    abstract fun chunkDao(): ChunkDao

    companion object {
        const val DATABASE_NAME = "rag-agent.db"
    }
}
