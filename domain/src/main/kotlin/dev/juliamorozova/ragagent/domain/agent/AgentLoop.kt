package dev.juliamorozova.ragagent.domain.agent

/** One turn of a conversation with the agent, independent of any wire format. */
data class AgentMessage(
    val role: Role,
    val content: String,
) {
    enum class Role { USER, ASSISTANT, TOOL }
}

/**
 * Drives a single user query through the Claude API tool-use loop: send the
 * conversation + available [AgentTool]s, execute any tool calls Claude requests
 * (including RAG retrieval), feed results back, repeat until a final answer.
 *
 * Implemented in the `rag`/`data` modules (Claude API client, orchestration); the
 * domain layer only defines the contract.
 */
interface AgentLoop {
    /**
     * @param systemPrompt Per-call override of the framing given to the model — different
     * callers (answering a question vs. extracting a fact to save) need the model to
     * understand a different job. Null keeps the implementation's own default.
     * @param onNarration Invoked with any non-blank text Claude produces alongside a
     * tool_use call, as soon as that response arrives — before the tool result round-trip
     * continues. Claude often has nothing more to say once it sees the tool result, so
     * this is a caller's only chance to surface that narration (e.g. as live progress),
     * rather than waiting on [run]'s single final return value.
     */
    suspend fun run(
        conversation: List<AgentMessage>,
        tools: List<AgentTool>,
        systemPrompt: String? = null,
        onNarration: (String) -> Unit = {},
    ): AgentMessage
}
