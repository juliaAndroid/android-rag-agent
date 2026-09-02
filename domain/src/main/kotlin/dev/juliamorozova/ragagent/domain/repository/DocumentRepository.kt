package dev.juliamorozova.ragagent.domain.repository

/** Raw source documents that make up the RAG knowledge base, before chunking. */
interface DocumentRepository {
    suspend fun getAllDocuments(): List<RawDocument>
}

data class RawDocument(
    val id: String,
    val title: String,
    val text: String,
)
