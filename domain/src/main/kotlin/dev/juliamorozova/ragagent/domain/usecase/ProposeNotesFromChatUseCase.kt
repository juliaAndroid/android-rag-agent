package dev.juliamorozova.ragagent.domain.usecase

/**
 * Lets an agent decide whether a Q&A exchange contains anything worth remembering and
 * *propose* it. Nothing is persisted here: the caller shows the proposals to the user and
 * stores only what they confirm, via [SaveNoteUseCase].
 */
interface ProposeNotesFromChatUseCase {
    /**
     * @param onNarration Invoked with the agent's own commentary as soon as it's available
     * (e.g. "I'll save that you're Julia."), so the caller can show live progress.
     * @return The facts the agent suggests saving, each a short self-contained statement.
     * Empty when the exchange holds nothing worth remembering.
     */
    suspend operator fun invoke(query: String, answer: String, onNarration: (String) -> Unit = {}): List<String>
}
