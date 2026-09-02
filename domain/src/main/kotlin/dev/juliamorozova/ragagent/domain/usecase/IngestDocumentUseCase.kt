package dev.juliamorozova.ragagent.domain.usecase

import dev.juliamorozova.ragagent.domain.model.IngestResult

interface IngestDocumentUseCase {
    suspend operator fun invoke(documentId: String, text: String): IngestResult
}