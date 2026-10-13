package dev.juliamorozova.ragagent.rag.summary

import dev.juliamorozova.ragagent.domain.model.NotesSummary
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
import dev.juliamorozova.ragagent.domain.summary.OnDeviceSummarizer
import dev.juliamorozova.ragagent.domain.summary.RoutingMode
import dev.juliamorozova.ragagent.domain.summary.SummaryRouter
import dev.juliamorozova.ragagent.domain.usecase.RetrieveRelevantChunksUseCase
import dev.juliamorozova.ragagent.domain.usecase.SummarizeNotesUseCase
import javax.inject.Inject

private const val RETRIEVAL_POOL = 7

/**
 * Below this best score nothing is summarized. Measured: topics found 0.54+, short queries 0.33-0.62, unrelated up
 * to 0.46, junk up to 0.29. Kept low so it isn't fitted to a 15-chunk base.
 */
internal const val MIN_RELEVANCE = 0.35f

private const val MAX_CHUNKS = 5
private const val SEPARATOR = "\n\n"

/**
 * Retrieves chunks for a topic, keeps the best of the single best-matching note and routes them to a summarizer.
 * One note, because mixing notes made the on-device model blend them into false statements.
 */
class DefaultSummarizeNotesUseCase @Inject constructor(
    private val retrieveRelevantChunks: RetrieveRelevantChunksUseCase,
    private val router: SummaryRouter,
    private val onDevice: OnDeviceSummarizer,
) : SummarizeNotesUseCase {

    override suspend fun invoke(topic: String, mode: RoutingMode): NotesSummary? =
        summarize(selectChunks(topic), mode, topic.trim())

    override suspend fun selectChunks(topic: String): List<RetrievedChunk> {
        if (topic.isBlank()) return emptyList()
        val retrieved = retrieveRelevantChunks(topic.trim(), RETRIEVAL_POOL)
        return selectFromBestNote(retrieved, onDevice.maxInputChars)
    }

    override suspend fun summarize(chunks: List<RetrievedChunk>, mode: RoutingMode, topic: String?): NotesSummary? {
        if (chunks.isEmpty()) return null
        val text = chunks.joinToString(SEPARATOR) { it.chunk.text }
        return router.summarize(text, chunks, mode, topic)
    }

    private fun selectFromBestNote(retrieved: List<RetrievedChunk>, limit: Int): List<RetrievedChunk> {
        val best = retrieved.maxByOrNull { it.score } ?: return emptyList()
        if (best.score < MIN_RELEVANCE) return emptyList()
        val bestNote = best.chunk.documentId

        var used = 0
        val kept = mutableListOf<RetrievedChunk>()
        for (candidate in retrieved.filter { it.chunk.documentId == bestNote }.sortedByDescending { it.score }) {
            if (kept.size == MAX_CHUNKS) break
            val cost = candidate.chunk.text.length + if (kept.isEmpty()) 0 else SEPARATOR.length
            // Skip rather than stop: a shorter, lower-ranked chunk may still fit.
            if (used + cost > limit) continue
            used += cost
            kept += candidate
        }
        return kept.sortedBy { it.chunk.position }
    }
}
