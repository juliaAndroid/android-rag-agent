package dev.juliamorozova.ragagent.presentation.query

import dev.juliamorozova.ragagent.domain.model.NotesSummary
import dev.juliamorozova.ragagent.domain.summary.RoutingMode
import dev.juliamorozova.ragagent.domain.usecase.SummarizeNotesUseCase

class FakeSummarizeNotesUseCase(
    var result: NotesSummary? = null,
    var shouldFail: Boolean = false,
) : SummarizeNotesUseCase {

    var lastTopic: String? = null
        private set

    override suspend fun invoke(topic: String, mode: RoutingMode): NotesSummary? {
        lastTopic = topic
        if (shouldFail) error("Forced failure")
        return result
    }
}
