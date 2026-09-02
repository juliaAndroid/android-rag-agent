package dev.juliamorozova.ragagent.rag.agent.claude

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * [ClaudeContentBlock] is decoded via a custom [ClaudeContentBlockSerializer] (Anthropic
 * tags block kind with a "type" string, not a Kotlin-friendly class discriminator), and
 * encoded back out for the next request. This is the part of the agent loop most likely
 * to silently break the wire contract with the real API, so it gets a dedicated test
 * rather than relying on a live network call.
 */
class ClaudeContentBlockSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes a text block`() {
        val decoded = json.decodeFromString<ClaudeContentBlock>("""{"type":"text","text":"hello"}""")

        assertThat(decoded).isEqualTo(ClaudeContentBlock.Text(text = "hello"))
    }

    @Test
    fun `decodes a tool_use block, preserving the raw input JSON`() {
        val decoded = json.decodeFromString<ClaudeContentBlock>(
            """{"type":"tool_use","id":"toolu_1","name":"retrieve_context","input":{"query":"hilt vs dagger","top_k":3}}""",
        )

        check(decoded is ClaudeContentBlock.ToolUse)
        assertThat(decoded.id).isEqualTo("toolu_1")
        assertThat(decoded.name).isEqualTo("retrieve_context")
        assertThat(decoded.input.jsonObject["query"]?.jsonPrimitive?.content).isEqualTo("hilt vs dagger")
    }

    @Test
    fun `encodes a tool_result block with the type discriminator`() {
        val block: ClaudeContentBlock = ClaudeContentBlock.ToolResult(toolUseId = "toolu_1", content = "[]")

        val encoded = json.encodeToString(block)

        assertThat(encoded).contains(""""type":"tool_result"""")
        assertThat(encoded).contains(""""tool_use_id":"toolu_1"""")
    }

    @Test
    fun `round-trips a mixed content list, the shape a real response returns`() {
        val original: List<ClaudeContentBlock> = listOf(
            ClaudeContentBlock.Text(text = "Let me check the notes."),
            ClaudeContentBlock.ToolUse(
                id = "toolu_2",
                name = "retrieve_context",
                input = json.parseToJsonElement("""{"query":"clean architecture"}"""),
            ),
        )

        val roundTripped = json.decodeFromString<List<ClaudeContentBlock>>(json.encodeToString(original))

        assertThat(roundTripped).isEqualTo(original)
    }
}
