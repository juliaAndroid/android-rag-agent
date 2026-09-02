package dev.juliamorozova.ragagent.domain.usecase

/** Chunks a document, embeds each chunk, and persists them to the vector store. */
interface IngestDocumentUseCase {
    suspend operator fun invoke(documentId: String, text: String)
}
