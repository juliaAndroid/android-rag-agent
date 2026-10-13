package dev.juliamorozova.ragagent.rag.chunking

import dev.juliamorozova.ragagent.domain.model.Chunk
import dev.juliamorozova.ragagent.domain.usecase.ChunkTextUseCase
import java.util.UUID
import javax.inject.Inject

/** Fixed-size overlapping character windows, not sentence boundaries: simple and enough for this corpus. */
class SlidingWindowChunker @Inject constructor() : ChunkTextUseCase {

    override fun invoke(documentId: String, text: String): List<Chunk> {
        if (text.isBlank()) return emptyList()

        val windowSize = DEFAULT_WINDOW_SIZE_CHARS
        val overlap = DEFAULT_OVERLAP_CHARS
        val step = (windowSize - overlap).coerceAtLeast(1)

        val chunks = mutableListOf<Chunk>()
        var start = 0
        var position = 0
        while (start < text.length) {
            val end = (start + windowSize).coerceAtMost(text.length)
            chunks += Chunk(
                id = UUID.randomUUID().toString(),
                documentId = documentId,
                text = text.substring(start, end),
                position = position,
            )
            position++
            if (end == text.length) break
            start += step
        }
        return chunks
    }

    private companion object {
        const val DEFAULT_WINDOW_SIZE_CHARS = 800
        const val DEFAULT_OVERLAP_CHARS = 120
    }
}
