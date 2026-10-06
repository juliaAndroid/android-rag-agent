package dev.juliamorozova.ragagent.presentation.query

import dev.juliamorozova.ragagent.domain.usecase.ProposeNotesFromChatUseCase

class FakeProposeNotesFromChatUseCase(
    var notes: List<String> = emptyList(),
    var shouldFail: Boolean = false,
) : ProposeNotesFromChatUseCase {

    override suspend fun invoke(query: String, answer: String, onNarration: (String) -> Unit): List<String> {
        if (shouldFail) error("Forced failure")
        return notes
    }
}
