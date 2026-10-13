package dev.juliamorozova.ragagent.domain.usecase

interface SaveNoteUseCase {
    /** Throws [IllegalStateException] if the note could not be fully stored. */
    suspend operator fun invoke(content: String)
}
