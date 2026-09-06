package dev.juliamorozova.ragagent.rag.fakes

import dev.juliamorozova.ragagent.domain.model.EmbeddingVector
import dev.juliamorozova.ragagent.domain.usecase.EmbedTextUseCase

/** Returns a fixed [EmbeddingVector] for any input; [shouldFail] makes every call throw
 *  instead, so the batch variant's default (map over [invoke]) fails on the first text. */
class FakeEmbedTextUseCase(
    var shouldFail: Boolean = false,
) : EmbedTextUseCase {

    var invokeCallCount = 0
        private set

    override suspend fun invoke(text: String): EmbeddingVector {
        invokeCallCount++
        if (shouldFail) error("Forced embedding failure")
        return EmbeddingVector(floatArrayOf(0.1f, 0.2f, 0.3f))
    }
}
