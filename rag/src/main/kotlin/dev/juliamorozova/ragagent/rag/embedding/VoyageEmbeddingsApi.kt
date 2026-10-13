package dev.juliamorozova.ragagent.rag.embedding

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface VoyageEmbeddingsApi {

    @POST("voyage/embeddings")
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
