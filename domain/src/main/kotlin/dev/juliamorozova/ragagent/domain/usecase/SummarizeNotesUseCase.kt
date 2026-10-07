package dev.juliamorozova.ragagent.domain.usecase

import dev.juliamorozova.ragagent.domain.model.NotesSummary
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
import dev.juliamorozova.ragagent.domain.summary.RoutingMode

/**
 * "Summarize my notes on X": finds the notes most relevant to a topic and summarizes them,
 * on the device when possible and in the cloud otherwise.
 *
 * The two steps are also available separately so the same chunks can be summarized more than
 * once, e.g. by both engines in an evaluation.
 */
interface SummarizeNotesUseCase {

    /**
     * @param mode Pins one engine instead of letting the router choose (used to compare the two).
     * @return The summary, or null when the topic is blank or the knowledge base has nothing for it.
     */
    suspend operator fun invoke(topic: String, mode: RoutingMode = RoutingMode.AUTO): NotesSummary?

    /**
     * Searches the notes for [topic] and keeps the best chunks that fit the on-device input limit,
     * in reading order. Empty for a blank topic or when nothing is found.
     */
    suspend fun selectChunks(topic: String): List<RetrievedChunk>

    /** Summarizes exactly [chunks], in the given order. Null when [chunks] is empty. */
    suspend fun summarize(chunks: List<RetrievedChunk>, mode: RoutingMode = RoutingMode.AUTO): NotesSummary?
}
