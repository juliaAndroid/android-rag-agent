package dev.juliamorozova.ragagent.rag.agent

import dev.juliamorozova.ragagent.domain.agent.AgentTool
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
import dev.juliamorozova.ragagent.domain.usecase.RetrieveRelevantChunksUseCase
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exposes RAG retrieval to the Claude agent loop as a callable tool, so the model
 * decides *when* to pull context instead of context always being force-fed into
 * every prompt.
 */
@Singleton
class RetrieveContextTool @Inject constructor(
    private val retrieveRelevantChunks: RetrieveRelevantChunksUseCase,
    private val json: Json,
) : AgentTool {

    override val name: String = "retrieve_context"

    override val description: String =
        "Retrieves the most relevant passages from the local knowledge base for a given query."

    override val inputSchema: String = """
        {
          "type": "object",
          "properties": {
            "query": { "type": "string", "description": "The search query." },
            "top_k": { "type": "integer", "description": "Number of passages to return.", "default": 5 }
          },
          "required": ["query"]
        }
    """.trimIndent()

    /**
     * Chunks returned by the most recent [execute] call, so [DefaultGenerateAnswerUseCase]
     * can surface them as citations without re-running retrieval or threading results
     * through the [dev.juliamorozova.ragagent.domain.agent.AgentLoop] contract itself.
     *
     * MVP scope: one query in flight at a time, which matches the app's single-screen
     * query UI. A per-request scope (instead of a Hilt singleton) would be the fix if
     * this ever needs to handle concurrent queries.
     */
    var lastRetrieved: List<RetrievedChunk> = emptyList()
        private set

    override suspend fun execute(inputJson: String): String {
        val request = json.decodeFromString<ToolInput>(inputJson)
        val results = retrieveRelevantChunks(query = request.query, topK = request.topK ?: 5)
        lastRetrieved = results
        return json.encodeToString(results.map { ToolResultChunk(text = it.chunk.text, score = it.score) })
    }

    @Serializable
    private data class ToolInput(
        val query: String,
        @SerialName("top_k") val topK: Int? = null,
    )

    @Serializable
    private data class ToolResultChunk(
        val text: String,
        val score: Float,
    )
}
