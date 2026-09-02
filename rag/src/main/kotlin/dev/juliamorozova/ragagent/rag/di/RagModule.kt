package dev.juliamorozova.ragagent.rag.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.juliamorozova.ragagent.domain.agent.AgentLoop
import dev.juliamorozova.ragagent.domain.usecase.ChunkTextUseCase
import dev.juliamorozova.ragagent.domain.usecase.EmbedTextUseCase
import dev.juliamorozova.ragagent.domain.usecase.GenerateAnswerUseCase
import dev.juliamorozova.ragagent.domain.usecase.RetrieveRelevantChunksUseCase
import dev.juliamorozova.ragagent.domain.repository.VectorStoreRepository
import dev.juliamorozova.ragagent.domain.usecase.IngestDocumentUseCase
import dev.juliamorozova.ragagent.rag.agent.DefaultGenerateAnswerUseCase
import dev.juliamorozova.ragagent.rag.agent.claude.ClaudeAgentLoop
import dev.juliamorozova.ragagent.rag.chunking.SlidingWindowChunker
import dev.juliamorozova.ragagent.rag.embedding.VoyageEmbedTextUseCase
import dev.juliamorozova.ragagent.rag.ingestion.DefaultIngestDocumentUseCase
import dev.juliamorozova.ragagent.rag.repository.RoomVectorStoreRepository
import dev.juliamorozova.ragagent.rag.retrieval.DefaultRetrieveRelevantChunksUseCase
import javax.inject.Singleton

/**
 * Binds the RAG pipeline's concrete implementations to the `domain` module's use case
 * interfaces, so `presentation`/`app` only ever depend on abstractions.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RagModule {

    @Binds
    @Singleton
    abstract fun bindChunkTextUseCase(impl: SlidingWindowChunker): ChunkTextUseCase

    @Binds
    @Singleton
    abstract fun bindEmbedTextUseCase(impl: VoyageEmbedTextUseCase): EmbedTextUseCase

    @Binds
    @Singleton
    abstract fun bindVectorStoreRepository(impl: RoomVectorStoreRepository): VectorStoreRepository

    @Binds
    @Singleton
    abstract fun bindRetrieveRelevantChunksUseCase(
        impl: DefaultRetrieveRelevantChunksUseCase,
    ): RetrieveRelevantChunksUseCase

    @Binds
    @Singleton
    abstract fun bindGenerateAnswerUseCase(impl: DefaultGenerateAnswerUseCase): GenerateAnswerUseCase

    @Binds
    @Singleton
    abstract fun bindAgentLoop(impl: ClaudeAgentLoop): AgentLoop

    @Binds
    @Singleton
    abstract fun bindIngestDocumentUseCase(impl: DefaultIngestDocumentUseCase): IngestDocumentUseCase
}
