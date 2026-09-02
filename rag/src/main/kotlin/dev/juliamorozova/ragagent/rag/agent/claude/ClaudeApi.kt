package dev.juliamorozova.ragagent.rag.agent.claude

import retrofit2.http.Body
import retrofit2.http.POST

/** Retrofit contract for the Claude Messages API. Auth headers are added by an OkHttp
 *  interceptor (see `ClaudeNetworkModule`), not here. */
interface ClaudeApi {

    @POST("v1/messages")
    suspend fun createMessage(@Body request: ClaudeMessageRequest): ClaudeMessageResponse
}
