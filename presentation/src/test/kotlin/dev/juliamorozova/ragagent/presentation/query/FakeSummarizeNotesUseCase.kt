package dev.juliamorozova.ragagent.presentation.query

import dev.juliamorozova.ragagent.domain.model.NotesSummary
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
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

    // The ViewModel only uses invoke(); the split steps exist for the evaluation tooling.
    override suspend fun selectChunks(topic: String): List<RetrievedChunk> = error("Not used by the ViewModel")

    override suspend fun summarize(chunks: List<RetrievedChunk>, mode: RoutingMode, topic: String?): NotesSummary? =
        error("Not used by the ViewModel")
}
