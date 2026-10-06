package dev.juliamorozova.ragagent.rag.agent

import android.util.Log
import dev.juliamorozova.ragagent.domain.repository.VectorStoreRepository
import dev.juliamorozova.ragagent.domain.usecase.IngestDocumentUseCase
import dev.juliamorozova.ragagent.domain.usecase.SaveNoteUseCase
import javax.inject.Inject

private const val TAG = "SaveNote"
private const val CHAT_NOTE_DOCUMENT_ID_PREFIX = "chat-note-"

/** Stores a user-confirmed note as its own document in the vector store. */
class DefaultSaveNoteUseCase @Inject constructor(
    private val ingestDocument: IngestDocumentUseCase,
    private val vectorStoreRepository: VectorStoreRepository,
) : SaveNoteUseCase {

    override suspend fun invoke(content: String) {
        val result = ingestDocument(
            documentId = "$CHAT_NOTE_DOCUMENT_ID_PREFIX${System.currentTimeMillis()}",
            text = content,
        )
        logAllNotes()
        check(result.failures.isEmpty()) { "Saved with ${result.failures.size} failed chunk(s)" }
    }

    /** Debug visibility into what's actually in the store right after a save — makes
     *  stale/duplicate/conflicting notes (e.g. an old name alongside a corrected one)
     *  obvious in logcat instead of only surfacing indirectly via a wrong answer. */
    private suspend fun logAllNotes() {
        val notes = vectorStoreRepository.getAll().filter { it.documentId.startsWith(CHAT_NOTE_DOCUMENT_ID_PREFIX) }
        Log.d(TAG, "Chat notes now in the knowledge base (${notes.size}):")
        notes.forEach { note -> Log.d(TAG, "  [${note.documentId}] ${note.text}") }
    }
}
