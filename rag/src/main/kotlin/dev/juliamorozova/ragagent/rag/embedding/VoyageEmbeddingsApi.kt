package dev.juliamorozova.ragagent.rag.embedding

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit service for the Voyage AI embeddings endpoint.
 * https://docs.voyageai.com/reference/embeddings-api
 *
 * Lives in `rag` (not `data`) — see the module architecture decision: `rag` is the
 * single infrastructure module for this app, so provider-specific API clients belong
 * here rather than split across a second `data` module that duplicates the same role.
 */
interface VoyageEmbeddingsApi {

    @POST("v1/embeddings")
    suspend fun embed(@Body request: VoyageEmbeddingsRequest): VoyageEmbeddingsResponse
}

@Serializable
data class VoyageEmbeddingsRequest(
    val input: List<String>,
    val model: String = "voyage-4",
)

@Serializable
data class VoyageEmbeddingsResponse(
    val data: List<VoyageEmbeddingData>,
)

@Serializable
data class VoyageEmbeddingData(
    val embedding: List<Float>,
    val index: Int,
)
