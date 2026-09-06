package dev.juliamorozova.ragagent.rag.agent

import dev.juliamorozova.ragagent.domain.agent.AgentLoop
import dev.juliamorozova.ragagent.domain.agent.AgentMessage
import dev.juliamorozova.ragagent.domain.usecase.SaveNoteFromChatUseCase
import javax.inject.Inject

private const val SAVE_NOTE_SYSTEM_PROMPT = "You extract durable facts from a single chat exchange and save " +
    "them for later recall. You have one tool, save_note — call it with a concise, self-contained statement " +
    "of the fact (not a raw copy of the exchange) whenever the user explicitly states, restates, or corrects " +
    "a fact about themselves or their work — even if it seems like it might already be known. Always save " +
    "what the user is telling you right now; don't try to judge whether it's a duplicate of something " +
    "already in the knowledge base — that's retrieval's job, not yours. Only skip calling the tool when the " +
    "exchange genuinely contains no factual statement at all, such as small talk or a question whose reply " +
    "adds no new information.\n\n" +
    "Your final reply — whether or not you called the tool — is shown to the user directly in a brief, " +
    "short-lived toast notification, not to another developer. Write it as a single short, plain sentence " +
    "addressed to them (e.g. \"Saved that your name is Julia.\" or \"Nothing new to save there.\") — never " +
    "mention save_note, tools, or your own reasoning process."

/**
 * Drives a focused Claude agent call — separate from [DefaultGenerateAnswerUseCase]'s
 * question-answering loop — whose only job is deciding whether a Q&A exchange is worth
 * persisting, and doing so via [SaveNoteTool] if it is. Letting the model judge
 * "worth remembering" (rather than always saving the raw answer text) keeps the
 * knowledge base from filling up with one-off retrieval-only exchanges.
 */
class DefaultSaveNoteFromChatUseCase @Inject constructor(
    private val agentLoop: AgentLoop,
    private val saveNoteTool: SaveNoteTool,
) : SaveNoteFromChatUseCase {

    override suspend fun invoke(query: String, answer: String, onNarration: (String) -> Unit): String {
        val conversation = listOf(
            AgentMessage(
                role = AgentMessage.Role.USER,
                content = "Here is a Q&A exchange from the user's session with you:\n\n" +
                    "User: $query\nAssistant: $answer\n\n" +
                    "If the user stated, restated, or corrected any fact about themselves or their work in " +
                    "this exchange, call save_note with a short, self-contained statement of that fact — " +
                    "even if it looks like it confirms something already known. Only skip calling the tool " +
                    "if there's genuinely no factual statement here at all.",
            ),
        )
        val result = agentLoop.run(
            conversation = conversation,
            tools = listOf(saveNoteTool),
            systemPrompt = SAVE_NOTE_SYSTEM_PROMPT,
            onNarration = onNarration,
        )
        return result.content
    }
}
