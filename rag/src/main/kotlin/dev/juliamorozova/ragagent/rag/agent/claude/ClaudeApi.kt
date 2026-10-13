package dev.juliamorozova.ragagent.rag.agent.claude

import retrofit2.http.Body
import retrofit2.http.POST

interface ClaudeApi {

    @POST("claude/messages")
    suspend fun createMessage(@Body request: ClaudeMessageRequest): ClaudeMessageResponse
}
