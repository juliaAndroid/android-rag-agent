package dev.juliamorozova.ragagent.rag.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.juliamorozova.ragagent.rag.local.db.ChunkDao
import dev.juliamorozova.ragagent.rag.local.db.RagDatabase
import javax.inject.Singleton

/** Provides the Room database and DAO backing [dev.juliamorozova.ragagent.rag.repository.RoomVectorStoreRepository]. */
@Module
@InstallIn(SingletonComponent::class)
object RoomModule {

    @Provides
    @Singleton
    fun provideRagDatabase(@ApplicationContext context: Context): RagDatabase =
        Room.databaseBuilder(context, RagDatabase::class.java, RagDatabase.DATABASE_NAME).build()

    @Provides
    fun provideChunkDao(database: RagDatabase): ChunkDao = database.chunkDao()
}
