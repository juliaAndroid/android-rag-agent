package dev.juliamorozova.ragagent.domain.usecase

import dev.juliamorozova.ragagent.domain.model.EmbeddingVector

/** Turns text (a chunk or a query) into an [EmbeddingVector] via the chosen provider. */
interface EmbedTextUseCase {
    suspend operator fun invoke(text: String): EmbeddingVector
}
