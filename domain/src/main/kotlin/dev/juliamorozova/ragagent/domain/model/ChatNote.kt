package dev.juliamorozova.ragagent.domain.model

/** Notes saved from the chat are stored as their own documents, told apart from the seeded files by this id prefix. */
object ChatNote {
    const val DOCUMENT_ID_PREFIX = "chat-note-"

    fun documentId(createdAtMillis: Long): String = "$DOCUMENT_ID_PREFIX$createdAtMillis"

    fun isChatNote(documentId: String): Boolean = documentId.startsWith(DOCUMENT_ID_PREFIX)
}
