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
import dev.juliamorozova.ragagent.domain.usecase.ProposeNotesFromChatUseCase
import dev.juliamorozova.ragagent.domain.usecase.SaveNoteUseCase
import dagger.Provides
import dev.juliamorozova.ragagent.domain.summary.CloudSummarizer
import dev.juliamorozova.ragagent.domain.summary.OnDeviceSummarizer
import dev.juliamorozova.ragagent.domain.summary.SummaryRouter
import dev.juliamorozova.ragagent.rag.agent.ClaudeSummarizer
import dev.juliamorozova.ragagent.domain.usecase.SummarizeNotesUseCase
import dev.juliamorozova.ragagent.rag.agent.nano.NanoSummarizer
import dev.juliamorozova.ragagent.rag.summary.DefaultSummarizeNotesUseCase
import dev.juliamorozova.ragagent.rag.agent.DefaultGenerateAnswerUseCase
import dev.juliamorozova.ragagent.rag.agent.DefaultProposeNotesFromChatUseCase
import dev.juliamorozova.ragagent.rag.agent.DefaultSaveNoteUseCase
import dev.juliamorozova.ragagent.rag.agent.claude.ClaudeAgentLoop
import dev.juliamorozova.ragagent.rag.chunking.SlidingWindowChunker
import dev.juliamorozova.ragagent.rag.embedding.VoyageEmbedTextUseCase
import dev.juliamorozova.ragagent.rag.ingestion.DefaultIngestDocumentUseCase
import dev.juliamorozova.ragagent.rag.repository.RoomVectorStoreRepository
import dev.juliamorozova.ragagent.rag.retrieval.DefaultRetrieveRelevantChunksUseCase
import javax.inject.Singleton

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

    @Binds
    @Singleton
    abstract fun bindProposeNotesFromChatUseCase(
        impl: DefaultProposeNotesFromChatUseCase,
    ): ProposeNotesFromChatUseCase

    @Binds
    @Singleton
    abstract fun bindSaveNoteUseCase(impl: DefaultSaveNoteUseCase): SaveNoteUseCase

    @Binds
    @Singleton
    abstract fun bindCloudSummarizer(impl: ClaudeSummarizer): CloudSummarizer

    @Binds
    @Singleton
    abstract fun bindOnDeviceSummarizer(impl: NanoSummarizer): OnDeviceSummarizer

    @Binds
    @Singleton
    abstract fun bindSummarizeNotesUseCase(impl: DefaultSummarizeNotesUseCase): SummarizeNotesUseCase

    companion object {
        /** A singleton, so a "device not supported" verdict lasts for the session. */
        @Provides
        @Singleton
        fun provideSummaryRouter(onDevice: OnDeviceSummarizer, cloud: CloudSummarizer): SummaryRouter =
            SummaryRouter(onDevice, cloud)
    }
}
