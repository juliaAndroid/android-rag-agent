package dev.juliamorozova.ragagent.rag.retrieval

import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
import dev.juliamorozova.ragagent.domain.repository.VectorStoreRepository
import dev.juliamorozova.ragagent.domain.usecase.EmbedTextUseCase
import dev.juliamorozova.ragagent.domain.usecase.RetrieveRelevantChunksUseCase
import javax.inject.Inject

/** Embeds the query, then asks the vector store for the top-K most similar chunks. */
class DefaultRetrieveRelevantChunksUseCase @Inject constructor(
    private val embedText: EmbedTextUseCase,
    private val vectorStore: VectorStoreRepository,
) : RetrieveRelevantChunksUseCase {

    override suspend fun invoke(query: String, topK: Int): List<RetrievedChunk> {
        val queryEmbedding = embedText(query)
        return vectorStore.findSimilar(queryEmbedding, topK)
    }
}
