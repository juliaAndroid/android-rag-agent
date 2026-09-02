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

    suspend fun execute(inputJson: String): String
}
