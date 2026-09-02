package dev.juliamorozova.ragagent.rag.chunking

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SlidingWindowChunkerTest {

    private val chunker = SlidingWindowChunker()

    @Test
    fun `blank text produces no chunks`() {
        assertThat(chunker.invoke(documentId = "doc-1", text = "   ")).isEmpty()
    }

    @Test
    fun `short text produces a single chunk`() {
        val result = chunker.invoke(documentId = "doc-1", text = "hello world")

        assertThat(result).hasSize(1)
        assertThat(result.first().text).isEqualTo("hello world")
        assertThat(result.first().documentId).isEqualTo("doc-1")
    }

    @Test
    fun `long text is split into multiple overlapping chunks`() {
        val text = "x".repeat(2000)

        val result = chunker.invoke(documentId = "doc-1", text = text)

        assertThat(result.size).isGreaterThan(1)
        assertThat(result.map { it.position }).isEqualTo(result.indices.toList())
    }
}
