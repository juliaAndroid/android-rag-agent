package dev.juliamorozova.ragagent.domain.usecase

import dev.juliamorozova.ragagent.domain.model.Chunk

/**
 * Splits a raw document into [Chunk]s. Pure Kotlin logic, no AI involved — implemented
 * in the `rag` module.
 */
interface ChunkTextUseCase {
    operator fun invoke(documentId: String, text: String): List<Chunk>
}
