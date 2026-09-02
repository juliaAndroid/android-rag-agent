package dev.juliamorozova.ragagent.rag.chunking

import dev.juliamorozova.ragagent.domain.model.Chunk
import dev.juliamorozova.ragagent.domain.usecase.ChunkTextUseCase
import java.util.UUID
import javax.inject.Inject

/**
 * Fixed-size, overlapping sliding-window chunker.
 *
 * Splits on a fixed character window with overlap rather than sentence/paragraph
 * boundaries — simple and good enough for the portfolio corpus; a boundary-aware
 * strategy is the natural next iteration if chunk quality becomes the bottleneck.
 * Kept here rather than in `domain` because chunking strategy is an implementation
 * detail the domain layer shouldn't know about, only the [ChunkTextUseCase]
 * contract it fulfills.
 */
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
