package dev.juliamorozova.ragagent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.juliamorozova.ragagent.domain.repository.VectorStoreRepository
import dev.juliamorozova.ragagent.domain.usecase.IngestDocumentUseCase
import javax.inject.Inject

private const val KNOWLEDGE_BASE_ASSETS_DIR = "knowledge-base"

/**
 * One-time bootstrap: on first launch (empty vector store), reads the bundled
 * knowledge-base markdown files from assets and runs each through [IngestDocumentUseCase]
 * so retrieval has something real to find. Skips entirely once the store already has
 * content — chunk ids aren't deterministic (UUID.randomUUID() per chunk), so re-running
 * ingestion on every launch would duplicate everything rather than replace it.
 *
 * README.md is excluded — it's a note about the corpus itself, not a real knowledge
 * document, and ingesting it would pollute retrieval with meta-commentary.
 */
class KnowledgeBaseSeeder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val vectorStore: VectorStoreRepository,
    private val ingestDocument: IngestDocumentUseCase,
) {

    suspend fun seedIfEmpty() {
        if (vectorStore.count() > 0) return

        val fileNames = context.assets.list(KNOWLEDGE_BASE_ASSETS_DIR).orEmpty()
            .filter { it.endsWith(".md") && it != "README.md" }

        for (fileName in fileNames) {
            runCatching {
                val text = context.assets.open("$KNOWLEDGE_BASE_ASSETS_DIR/$fileName")
                    .bufferedReader()
                    .use { it.readText() }
                ingestDocument(documentId = fileName, text = text)
            }
        }
    }
}
