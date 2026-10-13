package dev.juliamorozova.ragagent.rag.ingestion

import dev.juliamorozova.ragagent.domain.model.IngestResult
import dev.juliamorozova.ragagent.domain.model.IngestionFailure
import dev.juliamorozova.ragagent.domain.repository.VectorStoreRepository
import dev.juliamorozova.ragagent.domain.usecase.ChunkTextUseCase
import dev.juliamorozova.ragagent.domain.usecase.EmbedTextUseCase
import dev.juliamorozova.ragagent.domain.usecase.IngestDocumentUseCase
import javax.inject.Inject

class DefaultIngestDocumentUseCase @Inject constructor(
    private val chunkText: ChunkTextUseCase,
    private val embedText: EmbedTextUseCase,
    private val vectorStoreRepository: VectorStoreRepository,
) : IngestDocumentUseCase {

    override suspend fun invoke(documentId: String, text: String): IngestResult {
        val chunks = chunkText(documentId, text)
        if (chunks.isEmpty()) return IngestResult(total = 0, succeeded = 0, failures = emptyList())

        // One batched call for all chunks of the document instead of one per chunk (see README "Known trade-offs"). If
        // the batch fails, none of its chunks were embedded, so all count as failed.
        val embeddingsResult = runCatching { embedText(chunks.map { it.text }) }

        return embeddingsResult.fold(
            onSuccess = { embeddings ->
                var succeeded = 0
                val failures = mutableListOf<IngestionFailure>()
                chunks.zip(embeddings).forEach { (chunk, embedding) ->
                    runCatching { vectorStoreRepository.store(chunk, embedding) }
                        .onSuccess { succeeded++ }
                        .onFailure { error ->
                            failures += IngestionFailure(chunk.id, error.message ?: "Unknown error")
                        }
                }
                IngestResult(total = chunks.size, succeeded = succeeded, failures = failures)
            },
            onFailure = { error ->
                IngestResult(
                    total = chunks.size,
                    succeeded = 0,
                    failures = chunks.map { IngestionFailure(it.id, error.message ?: "Unknown error") },
                )
            },
        )
    }
}
