package dev.juliamorozova.ragagent.rag.agent

import android.util.Log
import dev.juliamorozova.ragagent.domain.agent.AgentTool
import dev.juliamorozova.ragagent.domain.repository.VectorStoreRepository
import dev.juliamorozova.ragagent.domain.usecase.IngestDocumentUseCase
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SaveNoteTool"
private const val CHAT_NOTE_DOCUMENT_ID_PREFIX = "chat-note-"

/**
 * Exposes note-saving to the Claude agent loop as a callable tool, so the model decides
 * *whether* a given exchange contains anything worth remembering instead of every answer
 * being persisted unconditionally.
 */
@Singleton
class SaveNoteTool @Inject constructor(
    private val ingestDocument: IngestDocumentUseCase,
    private val vectorStoreRepository: VectorStoreRepository,
    private val json: Json,
) : AgentTool {

    override val name: String = "save_note"

    override val description: String =
        "Saves a concise, self-contained fact to the user's personal knowledge base for later recall."

    override val inputSchema: String = """
        {
          "type": "object",
          "properties": {
            "content": { "type": "string", "description": "A concise, self-contained statement of the fact to remember." }
          },
          "required": ["content"]
        }
    """.trimIndent()

    override suspend fun execute(inputJson: String): String {
        val request = json.decodeFromString<ToolInput>(inputJson)
        val result = ingestDocument(
            documentId = "$CHAT_NOTE_DOCUMENT_ID_PREFIX${System.currentTimeMillis()}",
            text = request.content,
        )
        logAllNotes()
        return if (result.failures.isEmpty()) {
            "Saved."
        } else {
            "Saved with ${result.failures.size} failed chunk(s)."
        }
    }

    /** Debug visibility into what's actually in the store right after a save — makes
     *  stale/duplicate/conflicting notes (e.g. an old name alongside a corrected one)
     *  obvious in logcat instead of only surfacing indirectly via a wrong answer. */
    private suspend fun logAllNotes() {
        val notes = vectorStoreRepository.getAll().filter { it.documentId.startsWith(CHAT_NOTE_DOCUMENT_ID_PREFIX) }
        Log.d(TAG, "Chat notes now in the knowledge base (${notes.size}):")
        notes.forEach { note -> Log.d(TAG, "  [${note.documentId}] ${note.text}") }
    }

    @Serializable
    private data class ToolInput(val content: String)
}
