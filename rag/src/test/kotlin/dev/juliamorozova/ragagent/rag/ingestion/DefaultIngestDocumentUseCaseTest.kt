package dev.juliamorozova.ragagent.rag.ingestion

import com.google.common.truth.Truth.assertThat
import dev.juliamorozova.ragagent.domain.model.Chunk
import dev.juliamorozova.ragagent.rag.fakes.FakeChunkTextUseCase
import dev.juliamorozova.ragagent.rag.fakes.FakeEmbedTextUseCase
import dev.juliamorozova.ragagent.rag.fakes.FakeVectorStoreRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultIngestDocumentUseCaseTest {

    private val chunkText = FakeChunkTextUseCase()
    private val embedText = FakeEmbedTextUseCase()
    private val vectorStoreRepository = FakeVectorStoreRepository()
    private val ingestDocument = DefaultIngestDocumentUseCase(chunkText, embedText, vectorStoreRepository)

    private fun chunksOf(count: Int) = (0 until count).map { index ->
        Chunk(id = "chunk-$index", documentId = "doc-1", text = "text $index", position = index)
    }

    @Test
    fun `all chunks succeed`() = runTest {
        chunkText.chunks = chunksOf(3)

        val result = ingestDocument(documentId = "doc-1", text = "irrelevant — chunking is faked")

        assertThat(result.total).isEqualTo(3)
        assertThat(result.succeeded).isEqualTo(3)
        assertThat(result.failures).isEmpty()
    }

    @Test
    fun `embedText batch failure marks every chunk as a failure`() = runTest {
        chunkText.chunks = chunksOf(3)
        embedText.shouldFail = true

        val result = ingestDocument(documentId = "doc-1", text = "irrelevant")

        assertThat(result.total).isEqualTo(3)
        assertThat(result.succeeded).isEqualTo(0)
        assertThat(result.failures).hasSize(3)
    }

    @Test
    fun `a single store failure is reported without failing the other chunks`() = runTest {
        chunkText.chunks = chunksOf(3)
        vectorStoreRepository.failOnStore = true // one-shot: only the first store() call fails

        val result = ingestDocument(documentId = "doc-1", text = "irrelevant")

        assertThat(result.total).isEqualTo(3)
        assertThat(result.succeeded).isEqualTo(2)
        assertThat(result.failures).hasSize(1)
    }

    @Test
    fun `empty input produces an empty result without embedding or storing`() = runTest {
        chunkText.chunks = emptyList()

        val result = ingestDocument(documentId = "doc-1", text = "")

        assertThat(result.total).isEqualTo(0)
        assertThat(result.succeeded).isEqualTo(0)
        assertThat(result.failures).isEmpty()
        assertThat(embedText.invokeCallCount).isEqualTo(0)
        assertThat(vectorStoreRepository.getAll()).isEmpty()
    }
}
