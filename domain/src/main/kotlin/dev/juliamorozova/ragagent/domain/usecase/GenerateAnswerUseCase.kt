package dev.juliamorozova.ragagent.domain.usecase

import dev.juliamorozova.ragagent.domain.model.RagAnswer

interface GenerateAnswerUseCase {
    suspend operator fun invoke(query: String, onNarration: (String) -> Unit = {}): RagAnswer
}
