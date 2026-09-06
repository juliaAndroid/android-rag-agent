package dev.juliamorozova.ragagent.rag.fakes

import dev.juliamorozova.ragagent.domain.model.Chunk
import dev.juliamorozova.ragagent.domain.usecase.ChunkTextUseCase

/** Returns whatever fixed [chunks] a test configures, ignoring the actual input text. */
class FakeChunkTextUseCase(
    var chunks: List<Chunk> = emptyList(),
) : ChunkTextUseCase {
    override fun invoke(documentId: String, text: String): List<Chunk> = chunks
}
