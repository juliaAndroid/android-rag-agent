package dev.juliamorozova.ragagent.domain.agent

interface AgentTool {
    val name: String
    val description: String
    val inputSchema: String
    val endsTurn: Boolean get() = false

    suspend fun execute(inputJson: String): String
}
