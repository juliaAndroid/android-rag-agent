@file:OptIn(InternalSerializationApi::class)

package dev.juliamorozova.ragagent.rag.agent

import dev.juliamorozova.ragagent.domain.agent.AgentTool
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
import dev.juliamorozova.ragagent.domain.usecase.RetrieveRelevantChunksUseCase
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

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
     * Chunks from the latest [execute], so answers can cite them. Assumes one query at a time; concurrent queries
     * need a per-request scope.
     */
    var lastRetrieved: List<RetrievedChunk> = emptyList()
        private set

    /** Clears the previous result so a query that never calls this tool doesn't inherit old citations. */
    fun clearLastRetrieved() {
        lastRetrieved = emptyList()
    }

    override suspend fun execute(inputJson: String): String {
        val request = json.decodeFromString<ToolInput>(inputJson)
        val results = retrieveRelevantChunks(query = request.query, topK = request.topK ?: 5)
        lastRetrieved = results
        return json.encodeToString(
            results.map {
                ToolResultChunk(
                    text = it.chunk.text,
                    score = it.score,
                    date = java.time.Instant.ofEpochMilli(it.chunk.createdAtMillis).toString(),
                )
            },
        )
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
        val date: String,
    )
}
