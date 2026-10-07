package dev.juliamorozova.ragagent.rag.summary

import dev.juliamorozova.ragagent.domain.model.NotesSummary
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
import dev.juliamorozova.ragagent.domain.summary.OnDeviceSummarizer
import dev.juliamorozova.ragagent.domain.summary.RoutingMode
import dev.juliamorozova.ragagent.domain.summary.SummaryRouter
import dev.juliamorozova.ragagent.domain.usecase.RetrieveRelevantChunksUseCase
import dev.juliamorozova.ragagent.domain.usecase.SummarizeNotesUseCase
import javax.inject.Inject

/** Chunks to retrieve; usually all of them fit in the on-device limit (800 chars each). */
private const val TOP_K = 7
private const val SEPARATOR = "\n\n"

/**
 * Retrieves the chunks for a topic, keeps as many of the best ones as fit in the on-device input
 * limit, and hands them to the [SummaryRouter]. Both engines get the same text, so their summaries
 * are comparable.
 */
class DefaultSummarizeNotesUseCase @Inject constructor(
    private val retrieveRelevantChunks: RetrieveRelevantChunksUseCase,
    private val router: SummaryRouter,
    private val onDevice: OnDeviceSummarizer,
) : SummarizeNotesUseCase {

    override suspend fun invoke(topic: String, mode: RoutingMode): NotesSummary? {
        if (topic.isBlank()) return null

        val retrieved = retrieveRelevantChunks(topic.trim(), TOP_K)
        val selected = selectWithinLimit(retrieved, onDevice.maxInputChars)
        if (selected.isEmpty()) return null

        val text = selected.joinToString(SEPARATOR) { it.chunk.text }
        return router.summarize(text, selected, mode)
    }

    /**
     * Takes chunks best-first while the joined text stays within [limit] characters, then orders the
     * kept ones by document and position. Reading chunks of one note together keeps unrelated notes
     * from being blended into a single story, which the on-device model tends to do.
     */
    private fun selectWithinLimit(retrieved: List<RetrievedChunk>, limit: Int): List<RetrievedChunk> {
        var used = 0
        val kept = mutableListOf<RetrievedChunk>()
        for (candidate in retrieved.sortedByDescending { it.score }) {
            val cost = candidate.chunk.text.length + if (kept.isEmpty()) 0 else SEPARATOR.length
            // Skip rather than stop: a shorter, lower-ranked chunk may still fit.
            if (used + cost > limit) continue
            used += cost
            kept += candidate
        }
        return kept.sortedWith(compareBy({ it.chunk.documentId }, { it.chunk.position }))
    }
}
