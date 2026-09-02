package dev.juliamorozova.ragagent.domain.usecase

import dev.juliamorozova.ragagent.domain.model.EmbeddingVector

/** Turns text (a chunk or a query) into an [EmbeddingVector] via the chosen provider. */
interface EmbedTextUseCase {
    suspend operator fun invoke(text: String): EmbeddingVector

    /**
     * Batch variant: embeds several texts in one call to the provider instead of one
     * call per text. Providers with a real batch endpoint (Voyage included — its API
     * accepts an array of inputs natively) should override this for real; the default
     * here just falls back to calling [invoke] once per text, so implementations that
     * don't override it stay correct, just not batched.
     */
    suspend operator fun invoke(texts: List<String>): List<EmbeddingVector> {
        return texts.map { invoke(it) }
    }
}
