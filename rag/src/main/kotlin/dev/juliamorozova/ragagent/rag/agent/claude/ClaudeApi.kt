package dev.juliamorozova.ragagent.rag.agent.claude

import retrofit2.http.Body
import retrofit2.http.POST

/** Retrofit contract for the Claude Messages API, reached through the serverless proxy
 *  (`POST claude/messages` there forwards to Anthropic's `v1/messages`). The proxy adds the
 *  auth headers, so none are set on the client. */
interface ClaudeApi {

    @POST("claude/messages")
    suspend fun createMessage(@Body request: ClaudeMessageRequest): ClaudeMessageResponse
}
