package dev.juliamorozova.ragagent.domain.usecase

import dev.juliamorozova.ragagent.domain.model.NotesSummary
import dev.juliamorozova.ragagent.domain.summary.RoutingMode

/**
 * "Summarize my notes on X": finds the notes most relevant to a topic and summarizes them,
 * on the device when possible and in the cloud otherwise.
 */
interface SummarizeNotesUseCase {

    /**
     * @param mode Pins one engine instead of letting the router choose (used to compare the two).
     * @return The summary, or null when the topic is blank or the knowledge base has nothing for it.
     */
    suspend operator fun invoke(topic: String, mode: RoutingMode = RoutingMode.AUTO): NotesSummary?
}
