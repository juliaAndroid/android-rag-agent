package dev.juliamorozova.ragagent.rag.agent

import dev.juliamorozova.ragagent.domain.agent.AgentLoop
import dev.juliamorozova.ragagent.domain.agent.AgentMessage
import dev.juliamorozova.ragagent.domain.model.RagAnswer
import dev.juliamorozova.ragagent.domain.usecase.GenerateAnswerUseCase
import javax.inject.Inject

class DefaultGenerateAnswerUseCase @Inject constructor(
    private val agentLoop: AgentLoop,
    private val retrieveContextTool: RetrieveContextTool,
) : GenerateAnswerUseCase {

    override suspend fun invoke(query: String, onNarration: (String) -> Unit): RagAnswer {
        retrieveContextTool.clearLastRetrieved()
        val conversation = listOf(AgentMessage(role = AgentMessage.Role.USER, content = query))
        val answer = agentLoop.run(conversation, listOf(retrieveContextTool), onNarration = onNarration)
        return RagAnswer(
            text = answer.content,
            citations = retrieveContextTool.lastRetrieved,
        )
    }
}
