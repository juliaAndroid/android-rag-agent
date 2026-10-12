package dev.juliamorozova.ragagent.rag.summary

import dev.juliamorozova.ragagent.domain.model.NotesSummary
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
import dev.juliamorozova.ragagent.domain.summary.OnDeviceSummarizer
import dev.juliamorozova.ragagent.domain.summary.RoutingMode
import dev.juliamorozova.ragagent.domain.summary.SummaryRouter
import dev.juliamorozova.ragagent.domain.usecase.RetrieveRelevantChunksUseCase
import dev.juliamorozova.ragagent.domain.usecase.SummarizeNotesUseCase
import javax.inject.Inject

/** How many nearest chunks to look at when choosing the note to summarize. */
private const val RETRIEVAL_POOL = 7

/**
 * Below this best score nothing is summarized. Measured with voyage-4: descriptive topics found in the notes score
 * 0.54 or more, short typed queries 0.33 to 0.62, unrelated topics 0.46 or less, and plain junk ("I like London") 0.29
 * or less. The floor is set low on purpose: with a 15-chunk knowledge base a tighter one would be fitted to the examples.
 */
internal const val MIN_RELEVANCE = 0.35f

/** Most chunks handed to a summarizer (800 characters each at most). */
private const val MAX_CHUNKS = 5
private const val SEPARATOR = "\n\n"

/**
 * Retrieves the chunks for a topic, keeps the best ones of the single best-matching note, and hands
 * them to the [SummaryRouter]. Both engines get the same text, so their summaries are comparable.
 *
 * Why one note: the first evaluation showed that mixing chunks of unrelated notes made the on-device
 * model blend them into false statements, while the best-scoring chunks nearly always came from one note.
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

    /**
     * Finds the note that owns the best-scoring chunk, then takes that note's chunks best-first, at most
     * [MAX_CHUNKS] and while the joined text stays within [limit] characters, and returns them in reading
     * order. A topic spread over several notes is summarized from the best one only.
     */
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
