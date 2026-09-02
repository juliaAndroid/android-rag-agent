package dev.juliamorozova.ragagent.rag.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: ChunkEntity)

    /** Brute-force top-K: fine at portfolio-corpus scale — see README "Known
     * trade-offs" for the trade-off vs. an embedded/backend vector DB. */
    @Query("SELECT * FROM chunks")
    suspend fun getAll(): List<ChunkEntity>

    @Query("DELETE FROM chunks")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM chunks")
    suspend fun count(): Int
}
