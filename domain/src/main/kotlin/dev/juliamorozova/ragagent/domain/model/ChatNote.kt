package dev.juliamorozova.ragagent.domain.model

object ChatNote {
    const val DOCUMENT_ID_PREFIX = "chat-note-"

    fun documentId(createdAtMillis: Long): String = "$DOCUMENT_ID_PREFIX$createdAtMillis"

    fun isChatNote(documentId: String): Boolean = documentId.startsWith(DOCUMENT_ID_PREFIX)
}
