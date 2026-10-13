package dev.juliamorozova.ragagent.domain.usecase

import dev.juliamorozova.ragagent.domain.model.NotesSummary
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
import dev.juliamorozova.ragagent.domain.summary.RoutingMode

/**
 * "Summarize my notes on X": finds the notes for a topic and summarizes them, on the device or in the cloud. The
 * two steps are also separate, so both engines can summarize the same chunks.
 */
interface SummarizeNotesUseCase {

    /** [mode] pins one engine. Null when the topic is blank or nothing matches. */
    suspend operator fun invoke(topic: String, mode: RoutingMode = RoutingMode.AUTO): NotesSummary?

    /**
     * Keeps the best chunks of the best-matching note, in reading order and within the on-device limit; empty if
     * nothing is found.
     */
    suspend fun selectChunks(topic: String): List<RetrievedChunk>

    /** Summarizes exactly [chunks] in order (null when empty); [topic] goes to the cloud summarizer only. */
    suspend fun summarize(
        chunks: List<RetrievedChunk>,
        mode: RoutingMode = RoutingMode.AUTO,
        topic: String? = null,
    ): NotesSummary?
}
