package dev.juliamorozova.ragagent.rag.embedding

import dev.juliamorozova.ragagent.domain.model.EmbeddingVector
import dev.juliamorozova.ragagent.domain.usecase.EmbedTextUseCase
import javax.inject.Inject

/**
 * Calls the Voyage AI embeddings API (Anthropic's recommended embeddings provider
 * for RAG) to turn text into an [EmbeddingVector].
 */
class VoyageEmbedTextUseCase @Inject constructor(
    private val api: VoyageEmbeddingsApi,
) : EmbedTextUseCase {

    override suspend fun invoke(text: String): EmbeddingVector {
        val response = api.embed(VoyageEmbeddingsRequest(input = listOf(text)))
        val embedding = response.data.first().embedding
        return EmbeddingVector(values = embedding.toFloatArray())
    }
}
