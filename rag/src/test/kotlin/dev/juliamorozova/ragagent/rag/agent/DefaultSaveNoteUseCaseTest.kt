package dev.juliamorozova.ragagent.rag.agent

import com.google.common.truth.Truth.assertThat
import dev.juliamorozova.ragagent.domain.model.IngestResult
import dev.juliamorozova.ragagent.domain.model.IngestionFailure
import dev.juliamorozova.ragagent.domain.usecase.IngestDocumentUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultSaveNoteUseCaseTest {

    private class FakeIngest(var failures: List<IngestionFailure> = emptyList()) : IngestDocumentUseCase {
        var lastDocumentId: String? = null
        var lastText: String? = null

        override suspend fun invoke(documentId: String, text: String): IngestResult {
            lastDocumentId = documentId
            lastText = text
            return IngestResult(total = 1, succeeded = 1 - failures.size, failures = failures)
        }
    }

    private val ingest = FakeIngest()
    private val useCase = DefaultSaveNoteUseCase(ingest)

    @Test
    fun `stores the confirmed text as a chat-note document`() = runTest {
        useCase("Your name is Julia.")

        assertThat(ingest.lastText).isEqualTo("Your name is Julia.")
        assertThat(ingest.lastDocumentId).startsWith("chat-note-")
    }

    @Test
    fun `a failed chunk surfaces as an error instead of a silent success`() = runTest {
        ingest.failures = listOf(IngestionFailure(chunkId = "c1", reason = "boom"))

        val error = runCatching { useCase("Your name is Julia.") }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
    }
}
