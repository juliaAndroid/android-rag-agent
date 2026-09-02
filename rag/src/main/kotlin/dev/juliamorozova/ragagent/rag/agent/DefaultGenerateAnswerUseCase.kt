package dev.juliamorozova.ragagent.rag.agent

import dev.juliamorozova.ragagent.domain.agent.AgentLoop
import dev.juliamorozova.ragagent.domain.agent.AgentMessage
import dev.juliamorozova.ragagent.domain.model.RagAnswer
import dev.juliamorozova.ragagent.domain.usecase.GenerateAnswerUseCase
import javax.inject.Inject

/**
 * Drives the Claude agent loop for a single query: seeds the conversation with the
 * user's question, gives it [RetrieveContextTool], and turns the final answer into a
 * [RagAnswer] with citations for whatever chunks retrieval last surfaced.
 *
 * MVP scope: one tool, one query, no multi-turn chat history yet —
 * that's a natural follow-up once the UI keeps a conversation instead of a single field.
 */
class DefaultGenerateAnswerUseCase @Inject constructor(
    private val agentLoop: AgentLoop,
    private val retrieveContextTool: RetrieveContextTool,
) : GenerateAnswerUseCase {

    override suspend fun invoke(query: String): RagAnswer {
        val conversation = listOf(AgentMessage(role = AgentMessage.Role.USER, content = query))
        val answer = agentLoop.run(conversation, listOf(retrieveContextTool))
        return RagAnswer(
            text = answer.content,
            citations = retrieveContextTool.lastRetrieved,
        )
    }
}
