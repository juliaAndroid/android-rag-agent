package dev.juliamorozova.ragagent.domain.agent

data class AgentMessage(
    val role: Role,
    val content: String,
) {
    enum class Role { USER, ASSISTANT, TOOL }
}

/**
 * Runs one query through the tool-use loop: calls the model, executes requested [AgentTool]s, repeats until a
 * final answer.
 */
interface AgentLoop {
    /**
     * [systemPrompt] overrides the default framing (null keeps it); [onNarration] gets text the model writes
     * beside a tool call.
     */
    suspend fun run(
        conversation: List<AgentMessage>,
        tools: List<AgentTool>,
        systemPrompt: String? = null,
        onNarration: (String) -> Unit = {},
    ): AgentMessage
}
