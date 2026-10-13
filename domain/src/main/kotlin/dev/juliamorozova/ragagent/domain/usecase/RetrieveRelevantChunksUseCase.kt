package dev.juliamorozova.ragagent.domain.usecase

import dev.juliamorozova.ragagent.domain.model.RetrievedChunk

interface RetrieveRelevantChunksUseCase {
    suspend operator fun invoke(query: String, topK: Int = 5): List<RetrievedChunk>
}
