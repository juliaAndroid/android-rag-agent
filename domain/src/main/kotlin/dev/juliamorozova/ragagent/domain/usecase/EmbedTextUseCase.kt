package dev.juliamorozova.ragagent.domain.usecase

import dev.juliamorozova.ragagent.domain.model.EmbeddingVector

interface EmbedTextUseCase {
    suspend operator fun invoke(text: String): EmbeddingVector

    /**
     * Batch variant: one provider call for several texts. The default calls [invoke] per text (correct, not
     * batched).
     */
    suspend operator fun invoke(texts: List<String>): List<EmbeddingVector> {
        return texts.map { invoke(it) }
    }
}
