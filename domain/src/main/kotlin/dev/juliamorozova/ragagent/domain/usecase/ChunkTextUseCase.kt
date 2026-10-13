package dev.juliamorozova.ragagent.domain.usecase

import dev.juliamorozova.ragagent.domain.model.Chunk

interface ChunkTextUseCase {
    operator fun invoke(documentId: String, text: String): List<Chunk>
}
