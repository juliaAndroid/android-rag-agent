package dev.juliamorozova.ragagent.rag.agent

import dev.juliamorozova.ragagent.domain.agent.AgentTool
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * The `save_note` tool as the model sees it, but it only *collects* what the model wants
 * to save — nothing is stored. The user confirms first; storing is [DefaultSaveNoteUseCase]'s
 * job. Create one per proposal run: [proposed] is per-instance state, not shared.
 */
class ProposeNoteTool(private val json: Json) : AgentTool {

    val proposed = mutableListOf<String>()

    override val name: String = "save_note"

    override val description: String =
        "Saves a concise, self-contained fact to the user's personal knowledge base for later recall."

    override val inputSchema: String = """
        {
          "type": "object",
          "properties": {
            "content": { "type": "string", "description": "A concise, self-contained statement of the fact to remember, written in the third person starting with 'The user'." }
          },
          "required": ["content"]
        }
    """.trimIndent()

    /** The call itself is the answer; no follow-up model turn is needed. */
    override val endsTurn: Boolean = true

    override suspend fun execute(inputJson: String): String {
        val content = json.decodeFromString<ToolInput>(inputJson).content.trim()
        if (content.isNotEmpty()) proposed += content
        return "Proposed to the user."
    }

    @Serializable
    private data class ToolInput(val content: String)
}
