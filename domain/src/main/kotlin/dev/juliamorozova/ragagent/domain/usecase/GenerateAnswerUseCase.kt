package dev.juliamorozova.ragagent.domain.usecase

import dev.juliamorozova.ragagent.domain.model.RagAnswer

/**
 * Retrieves context for the query, drives the Claude agent loop (with the RAG
 * retrieval tool available) and returns a grounded, citable answer.
 */
interface GenerateAnswerUseCase {
    suspend operator fun invoke(query: String): RagAnswer
}
