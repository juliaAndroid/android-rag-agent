package dev.juliamorozova.ragagent.domain.usecase

import dev.juliamorozova.ragagent.domain.model.RetrievedChunk

/** Embeds the query and returns the top-K most relevant chunks from the vector store. */
interface RetrieveRelevantChunksUseCase {
    suspend operator fun invoke(query: String, topK: Int = 5): List<RetrievedChunk>
}
