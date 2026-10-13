package dev.juliamorozova.ragagent.rag.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: ChunkEntity)

    @Query("SELECT * FROM chunks")
    suspend fun getAll(): List<ChunkEntity>

    @Query("DELETE FROM chunks")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM chunks")
    suspend fun count(): Int
}
