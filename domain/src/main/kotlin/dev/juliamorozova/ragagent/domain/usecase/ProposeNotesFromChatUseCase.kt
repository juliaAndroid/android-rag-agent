package dev.juliamorozova.ragagent.domain.usecase

/**
 * Lets an agent propose notes worth remembering from a Q&A exchange; nothing is stored until the user confirms
 * ([SaveNoteUseCase]).
 */
interface ProposeNotesFromChatUseCase {
    /**
     * [onNarration] gets the agent's commentary for live progress. Returns short self-contained facts; empty if
     * nothing is worth saving.
     */
    suspend operator fun invoke(query: String, answer: String, onNarration: (String) -> Unit = {}): List<String>
}
