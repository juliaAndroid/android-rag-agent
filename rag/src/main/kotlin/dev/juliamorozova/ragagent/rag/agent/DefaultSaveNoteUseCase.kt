package dev.juliamorozova.ragagent.rag.agent

import dev.juliamorozova.ragagent.domain.model.ChatNote
import dev.juliamorozova.ragagent.domain.usecase.IngestDocumentUseCase
import dev.juliamorozova.ragagent.domain.usecase.SaveNoteUseCase
import javax.inject.Inject

/** Stores a user-confirmed note as its own document in the vector store. */
class DefaultSaveNoteUseCase @Inject constructor(
    private val ingestDocument: IngestDocumentUseCase,
) : SaveNoteUseCase {

    override suspend fun invoke(content: String) {
        val result = ingestDocument(
            documentId = ChatNote.documentId(System.currentTimeMillis()),
            text = content,
        )
        check(result.failures.isEmpty()) { "Saved with ${result.failures.size} failed chunk(s)" }
    }
}
