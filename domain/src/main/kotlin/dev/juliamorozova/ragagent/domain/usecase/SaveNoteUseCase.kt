package dev.juliamorozova.ragagent.domain.usecase

/** Persists one note the user has confirmed to the personal knowledge base. */
interface SaveNoteUseCase {
    /** @throws IllegalStateException if the note could not be fully stored. */
    suspend operator fun invoke(content: String)
}
