package dev.juliamorozova.ragagent.domain.agent

/**
 * A tool the Claude agent loop can call (function/tool use). `retrieve_context` backed
 * by the RAG pipeline is the first one; more can be added without touching the agent
 * loop itself.
 */
interface AgentTool {
    val name: String
    val description: String

    /** JSON schema (as a string) describing the tool's input, per the Claude API tool spec. */
    val inputSchema: String

    /**
     * When true, the agent loop returns right after executing this tool instead of sending
     * the result back to the model for a follow-up turn. For tools whose call *is* the
     * answer (e.g. proposing a note for the user to confirm), so the extra round trip that
     * would only produce closing commentary is skipped.
     */
    val endsTurn: Boolean get() = false

    suspend fun execute(inputJson: String): String
}
