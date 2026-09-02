@file:OptIn(InternalSerializationApi::class) package dev.juliamorozova.ragagent.rag.agent.claude

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Wire-format DTOs for the Claude Messages API (`POST /v1/messages`), scoped to what the
 * agent loop actually needs: text + tool-use content blocks, no image/document support yet.
 *
 * https://docs.claude.com/en/api/messages
 */

@Serializable
data class ClaudeMessageRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String? = null,
    val messages: List<ClaudeMessage>,
    val tools: List<ClaudeTool>? = null,
)

@Serializable
data class ClaudeMessageResponse(
    val id: String,
    val role: String,
    val content: List<ClaudeContentBlock>,
    @SerialName("stop_reason") val stopReason: String? = null,
)

@Serializable
data class ClaudeMessage(
    /** "user" or "assistant" — Claude has no separate wire role for tool results; they
     *  travel back as "user" messages containing `tool_result` content blocks. */
    val role: String,
    val content: List<ClaudeContentBlock>,
)

@Serializable
data class ClaudeTool(
    val name: String,
    val description: String,
    @SerialName("input_schema") val inputSchema: JsonElement,
)

/**
 * A single content block inside a [ClaudeMessage]. Anthropic distinguishes block kinds by
 * a "type" string in the JSON rather than a Kotlin-friendly discriminator, so decoding goes
 * through [ClaudeContentBlockSerializer] instead of [kotlinx.serialization]'s default sealed
 * polymorphism.
 */
@Serializable(with = ClaudeContentBlockSerializer::class)
sealed interface ClaudeContentBlock {

    @Serializable
    data class Text(
        val type: String = "text",
        val text: String,
    ) : ClaudeContentBlock

    @Serializable
    data class ToolUse(
        val type: String = "tool_use",
        val id: String,
        val name: String,
        val input: JsonElement,
    ) : ClaudeContentBlock

    @Serializable
    data class ToolResult(
        val type: String = "tool_result",
        @SerialName("tool_use_id") val toolUseId: String,
        val content: String,
        @SerialName("is_error") val isError: Boolean = false,
    ) : ClaudeContentBlock
}

object ClaudeContentBlockSerializer :
    JsonContentPolymorphicSerializer<ClaudeContentBlock>(ClaudeContentBlock::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ClaudeContentBlock> {
        return when (val type = element.jsonObject["type"]?.jsonPrimitive?.content) {
            "text" -> ClaudeContentBlock.Text.serializer()
            "tool_use" -> ClaudeContentBlock.ToolUse.serializer()
            "tool_result" -> ClaudeContentBlock.ToolResult.serializer()
            else -> error("Unknown Claude content block type: '$type'")
        }
    }
}
