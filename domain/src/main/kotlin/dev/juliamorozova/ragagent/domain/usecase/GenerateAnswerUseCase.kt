package dev.juliamorozova.ragagent.domain.usecase

import dev.juliamorozova.ragagent.domain.model.RagAnswer

/**
 * Retrieves context for the query, drives the Claude agent loop (with the RAG
 * retrieval tool available) and returns a grounded, citable answer.
 */
interface GenerateAnswerUseCase {
    /**
     * @param onNarration Invoked with any text Claude produces alongside a tool call
     * (e.g. "I'll check your personal knowledge base...") as soon as it's available —
     * lets the caller show live progress instead of just a spinner.
     */
    suspend operator fun invoke(query: String, onNarration: (String) -> Unit = {}): RagAnswer
}
