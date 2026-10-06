package dev.juliamorozova.ragagent.presentation.query

import dev.juliamorozova.ragagent.domain.usecase.SaveNoteUseCase

class FakeSaveNoteUseCase(
    var shouldFail: Boolean = false,
) : SaveNoteUseCase {

    val saved = mutableListOf<String>()

    override suspend fun invoke(content: String) {
        if (shouldFail) error("Forced failure")
        saved += content
    }
}
