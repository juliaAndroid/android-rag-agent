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
    suspend fun run(conversation: List<AgentMessage>, tools: List<AgentTool>): AgentMessage
}
