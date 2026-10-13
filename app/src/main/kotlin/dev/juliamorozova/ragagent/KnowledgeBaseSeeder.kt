package dev.juliamorozova.ragagent

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.juliamorozova.ragagent.domain.repository.VectorStoreRepository
import dev.juliamorozova.ragagent.domain.usecase.IngestDocumentUseCase
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

private const val TAG = "KnowledgeBaseSeeder"
private const val KNOWLEDGE_BASE_ASSETS_DIR = "knowledge-base"

/**
 * Indexes bundled knowledge-base files missing from the store (id = file name), so a failed launch is completed
 * later.
 */
class KnowledgeBaseSeeder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val vectorStore: VectorStoreRepository,
    private val ingestDocument: IngestDocumentUseCase,
) {

    suspend fun seedMissing() {
        val present = vectorStore.getAll().map { it.documentId }.toSet()
        val missing = context.assets.list(KNOWLEDGE_BASE_ASSETS_DIR).orEmpty()
            .filter { it.endsWith(".md") && it !in present }

        for (fileName in missing) {
            try {
                val text = context.assets.open("$KNOWLEDGE_BASE_ASSETS_DIR/$fileName")
                    .bufferedReader()
                    .use { it.readText() }
                val result = ingestDocument(documentId = fileName, text = text)
                if (result.failures.isNotEmpty()) {
                    // A partly stored document counts as present and is not retried.
                    Log.w(TAG, "$fileName: ${result.failures.size} of ${result.total} chunks failed")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "$fileName was not indexed (${e::class.simpleName}); retried on next launch")
            }
        }
    }
}
