package dev.juliamorozova.ragagent.domain.usecase

/**
 * Lets an agent decide whether a Q&A exchange contains anything worth remembering, and
 * save it if so.
 */
interface SaveNoteFromChatUseCase {
    /**
     * @param onNarration Invoked with the agent's own commentary as soon as it's
     * available, before the save itself completes (e.g. "I'll save that you're
     * Julia.") — lets the caller show live progress instead of just a spinner.
     * @return The agent's own closing text once the save (or its decision not to save)
     * is done, e.g. "Done! I've saved the fact that your name is Lucy." — may be blank,
     * since the model doesn't always produce closing text once it sees the tool result.
     */
    suspend operator fun invoke(query: String, answer: String, onNarration: (String) -> Unit = {}): String
}
