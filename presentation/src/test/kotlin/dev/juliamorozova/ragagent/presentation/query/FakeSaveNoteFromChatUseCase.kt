package dev.juliamorozova.ragagent.presentation.query

import dev.juliamorozova.ragagent.domain.usecase.SaveNoteFromChatUseCase

/** Not exercised by the current QueryViewModel test cases, but QueryViewModel still
 *  needs one to construct — kept minimal. */
class FakeSaveNoteFromChatUseCase(
    var result: String = "",
) : SaveNoteFromChatUseCase {
    override suspend fun invoke(query: String, answer: String, onNarration: (String) -> Unit): String = result
}
