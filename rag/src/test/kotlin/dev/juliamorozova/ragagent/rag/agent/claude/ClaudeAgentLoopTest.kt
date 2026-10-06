package dev.juliamorozova.ragagent.rag.agent.claude

import com.google.common.truth.Truth.assertThat
import dev.juliamorozova.ragagent.domain.agent.AgentMessage
import dev.juliamorozova.ragagent.domain.agent.AgentTool
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

class ClaudeAgentLoopTest {

    /** Replays pre-scripted responses and counts how many requests were made. */
    private class ScriptedClaudeApi(vararg responses: ClaudeMessageResponse) : ClaudeApi {
        private val queue = ArrayDeque(responses.toList())
        val requests = mutableListOf<ClaudeMessageRequest>()

        override suspend fun createMessage(request: ClaudeMessageRequest): ClaudeMessageResponse {
            requests += request
            return queue.removeFirst()
        }
    }

    private class RecordingTool(
        override val name: String,
        override val endsTurn: Boolean = false,
    ) : AgentTool {
        override val description = "test tool"
        override val inputSchema = """{"type":"object","properties":{}}"""
        var executions = 0

        override suspend fun execute(inputJson: String): String {
            executions++
            return "result"
        }
    }

    private fun toolUseResponse(toolName: String) = ClaudeMessageResponse(
        id = "msg_1",
        role = "assistant",
        content = listOf(
            ClaudeContentBlock.ToolUse(id = "toolu_1", name = toolName, input = buildJsonObject { put("x", "y") }),
        ),
        stopReason = "tool_use",
    )

    private fun textResponse(text: String) = ClaudeMessageResponse(
        id = "msg_2",
        role = "assistant",
        content = listOf(ClaudeContentBlock.Text(text = text)),
        stopReason = "end_turn",
    )

    private val conversation = listOf(AgentMessage(role = AgentMessage.Role.USER, content = "hi"))

    @Test
    fun `a normal tool result is sent back and the loop continues to a final answer`() = runTest {
        val api = ScriptedClaudeApi(toolUseResponse("lookup"), textResponse("final answer"))
        val tool = RecordingTool("lookup")

        val result = ClaudeAgentLoop(api, Json).run(conversation, listOf(tool))

        assertThat(api.requests).hasSize(2)
        assertThat(tool.executions).isEqualTo(1)
        assertThat(result.content).isEqualTo("final answer")

        // The tool_use must be answered with a matching tool_result in the next request.
        val toolResult = api.requests[1].messages.last().content.single() as ClaudeContentBlock.ToolResult
        assertThat(toolResult.toolUseId).isEqualTo("toolu_1")
        assertThat(toolResult.content).isEqualTo("result")
    }

    @Test
    fun `a tool that ends the turn stops the loop without a second request`() = runTest {
        val api = ScriptedClaudeApi(toolUseResponse("propose"))
        val tool = RecordingTool("propose", endsTurn = true)

        ClaudeAgentLoop(api, Json).run(conversation, listOf(tool))

        assertThat(api.requests).hasSize(1)
        assertThat(tool.executions).isEqualTo(1)
    }
}
