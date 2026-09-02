package dev.juliamorozova.ragagent.rag.agent.claude

import dev.juliamorozova.ragagent.domain.agent.AgentLoop
import dev.juliamorozova.ragagent.domain.agent.AgentMessage
import dev.juliamorozova.ragagent.domain.agent.AgentTool
import dev.juliamorozova.ragagent.rag.BuildConfig
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val MAX_TOKENS = 1024

/** Safety valve: if Claude keeps calling tools without ever settling on an answer,
 *  stop asking rather than loop (and burn tokens) forever. */
private const val MAX_TOOL_HOPS = 5

private const val SYSTEM_PROMPT = """
You are a technical assistant answering questions about the user's own Android/Kotlin
engineering experience, grounded in a personal knowledge base of their real notes.
Call the retrieve_context tool before answering any question about their projects,
architecture decisions, or past work — do not rely on general knowledge for those.
If retrieval doesn't return anything relevant, say so honestly instead of inventing
details or generic best-practice answers not backed by the retrieved context.
"""

/**
 * [AgentLoop] backed by the real Claude Messages API: sends the conversation with the
 * available tools, and whenever `stop_reason` is "tool_use", executes the matching
 * [AgentTool] locally and feeds the result back — repeating until Claude produces a
 * final text answer or [MAX_TOOL_HOPS] is exhausted.
 */
class ClaudeAgentLoop @Inject constructor(
    private val claudeApi: ClaudeApi,
    private val json: Json,
) : AgentLoop {

    override suspend fun run(conversation: List<AgentMessage>, tools: List<AgentTool>): AgentMessage {
        val toolsByName = tools.associateBy { it.name }
        val wireTools = tools.map { tool ->
            ClaudeTool(
                name = tool.name,
                description = tool.description,
                inputSchema = json.parseToJsonElement(tool.inputSchema),
            )
        }

        val messages = conversation.map { it.toClaudeMessage() }.toMutableList()

        repeat(MAX_TOOL_HOPS) {
            val response = claudeApi.createMessage(
                ClaudeMessageRequest(
                    model = BuildConfig.CLAUDE_MODEL,
                    maxTokens = MAX_TOKENS,
                    system = SYSTEM_PROMPT.trim(),
                    messages = messages,
                    tools = wireTools.ifEmpty { null },
                ),
            )

            // Not streaming this response on purpose — see README "Known trade-offs".

            if (response.stopReason != "tool_use") {
                val text = response.content
                    .filterIsInstance<ClaudeContentBlock.Text>()
                    .joinToString("\n") { it.text }
                return AgentMessage(role = AgentMessage.Role.ASSISTANT, content = text)
            }

            // Claude's turn (including its tool_use blocks) must be echoed back verbatim
            // before we can answer with tool_result blocks — the API requires both.
            messages += ClaudeMessage(role = "assistant", content = response.content)

            val toolResults = response.content
                .filterIsInstance<ClaudeContentBlock.ToolUse>()
                .map { toolUse ->
                    val tool = toolsByName[toolUse.name]
                    val resultText = if (tool != null) {
                        tool.execute(toolUse.input.toString())
                    } else {
                        "Error: no tool named '${toolUse.name}' is available."
                    }
                    ClaudeContentBlock.ToolResult(toolUseId = toolUse.id, content = resultText)
                }
            messages += ClaudeMessage(role = "user", content = toolResults)
        }

        return AgentMessage(
            role = AgentMessage.Role.ASSISTANT,
            content = "Sorry, I couldn't produce an answer after $MAX_TOOL_HOPS tool calls.",
        )
    }

    private fun AgentMessage.toClaudeMessage(): ClaudeMessage = ClaudeMessage(
        role = when (role) {
            AgentMessage.Role.USER -> "user"
            AgentMessage.Role.ASSISTANT -> "assistant"
            // The domain model allows a TOOL-authored seed message in principle, but the
            // agent loop itself is the only thing that produces tool_result content — a
            // caller seeding one directly isn't a real use case yet.
            AgentMessage.Role.TOOL -> "user"
        },
        content = listOf(ClaudeContentBlock.Text(text = content)),
    )
}
