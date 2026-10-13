package dev.juliamorozova.ragagent.domain.repository

interface DocumentRepository {
    suspend fun getAllDocuments(): List<RawDocument>
}

data class RawDocument(
    val id: String,
    val title: String,
    val text: String,
)
