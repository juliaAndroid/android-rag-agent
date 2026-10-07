package dev.juliamorozova.ragagent.rag.summary

import com.google.common.truth.Truth.assertThat
import dev.juliamorozova.ragagent.domain.model.Chunk
import dev.juliamorozova.ragagent.domain.model.FallbackReason
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
import dev.juliamorozova.ragagent.domain.model.SummaryEngine
import dev.juliamorozova.ragagent.domain.summary.CloudSummarizer
import dev.juliamorozova.ragagent.domain.summary.OnDeviceAvailability
import dev.juliamorozova.ragagent.domain.summary.OnDeviceSummarizer
import dev.juliamorozova.ragagent.domain.summary.RoutingMode
import dev.juliamorozova.ragagent.domain.summary.SummaryRouter
import dev.juliamorozova.ragagent.domain.usecase.RetrieveRelevantChunksUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultSummarizeNotesUseCaseTest {

    private class FakeRetrieve(var results: List<RetrievedChunk> = emptyList()) : RetrieveRelevantChunksUseCase {
        var lastQuery: String? = null
        var lastTopK: Int? = null
        var error: Exception? = null

        override suspend fun invoke(query: String, topK: Int): List<RetrievedChunk> {
            lastQuery = query
            lastTopK = topK
            error?.let { throw it }
            return results
        }
    }

    private class FakeOnDevice : OnDeviceSummarizer {
        override val minInputChars = 400
        override val maxInputChars = 7_860
        var availability: OnDeviceAvailability = OnDeviceAvailability.Available
        var lastText: String? = null
        var calls = 0

        override suspend fun availability() = availability
        override suspend fun downloadModel(onProgress: (percent: Int) -> Unit) = Unit
        override suspend fun summarize(text: String): List<String> {
            calls++
            lastText = text
            return listOf("on-device bullet")
        }
    }

    private class FakeCloud : CloudSummarizer {
        var lastText: String? = null
        override suspend fun summarize(text: String): List<String> {
            lastText = text
            return listOf("cloud bullet")
        }
    }

    private val retrieve = FakeRetrieve()
    private val onDevice = FakeOnDevice()
    private val cloud = FakeCloud()
    private val useCase = DefaultSummarizeNotesUseCase(retrieve, SummaryRouter(onDevice, cloud), onDevice)

    private fun chunk(id: String, document: String, position: Int, length: Int, score: Float, fill: Char = 'a') =
        RetrievedChunk(Chunk(id, document, fill.toString().repeat(length), position), score)

    @Test
    fun `a blank topic returns null without searching`() = runTest {
        assertThat(useCase("   ")).isNull()
        assertThat(retrieve.lastQuery).isNull()
    }

    @Test
    fun `no relevant chunks returns null without calling any model`() = runTest {
        retrieve.results = emptyList()

        assertThat(useCase("clean architecture")).isNull()
        assertThat(onDevice.calls).isEqualTo(0)
        assertThat(cloud.lastText).isNull()
    }

    @Test
    fun `the topic is trimmed and seven chunks are requested`() = runTest {
        retrieve.results = listOf(chunk("c1", "a.md", 0, 800, 0.9f))

        useCase("  clean architecture  ")

        assertThat(retrieve.lastQuery).isEqualTo("clean architecture")
        assertThat(retrieve.lastTopK).isEqualTo(7)
    }

    @Test
    fun `chunks are summarized on-device and returned as sources`() = runTest {
        val chunks = listOf(chunk("c1", "a.md", 0, 800, 0.9f), chunk("c2", "a.md", 1, 800, 0.8f))
        retrieve.results = chunks

        val summary = useCase("topic")!!

        assertThat(summary.engine).isEqualTo(SummaryEngine.ON_DEVICE)
        assertThat(summary.bullets).containsExactly("on-device bullet")
        assertThat(summary.sources).containsExactlyElementsIn(chunks)
    }

    @Test
    fun `chunks of one note are read together in document order, not in score order`() = runTest {
        retrieve.results = listOf(
            chunk("b1", "b.md", 0, 500, 0.9f, fill = 'B'),
            chunk("a2", "a.md", 1, 500, 0.8f, fill = 'A'),
            chunk("a1", "a.md", 0, 500, 0.7f, fill = 'C'),
        )

        val summary = useCase("topic")!!

        assertThat(summary.sources.map { it.chunk.id }).containsExactly("a1", "a2", "b1").inOrder()
        assertThat(onDevice.lastText).isEqualTo(
            "C".repeat(500) + "\n\n" + "A".repeat(500) + "\n\n" + "B".repeat(500),
        )
    }

    @Test
    fun `only the best chunks that fit the on-device limit are kept`() = runTest {
        // Four 3000-char chunks do not fit 7860; the two best do (3000 + 2 + 3000).
        retrieve.results = listOf(
            chunk("low", "a.md", 0, 3_000, 0.2f),
            chunk("best", "b.md", 0, 3_000, 0.9f),
            chunk("mid", "c.md", 0, 3_000, 0.5f),
            chunk("lowest", "d.md", 0, 3_000, 0.1f),
        )

        val summary = useCase("topic")!!

        assertThat(summary.sources.map { it.chunk.id }).containsExactly("best", "mid").inOrder()
        assertThat(summary.engine).isEqualTo(SummaryEngine.ON_DEVICE)
    }

    @Test
    fun `a smaller lower-ranked chunk still fits after a large one is skipped`() = runTest {
        retrieve.results = listOf(
            chunk("big1", "a.md", 0, 4_000, 0.9f),
            chunk("big2", "b.md", 0, 4_000, 0.8f),
            chunk("small", "c.md", 0, 500, 0.1f),
        )

        val summary = useCase("topic")!!

        assertThat(summary.sources.map { it.chunk.id }).containsExactly("big1", "small").inOrder()
    }

    @Test
    fun `falls back to the cloud with the same text when the on-device model is unavailable`() = runTest {
        onDevice.availability = OnDeviceAvailability.Unavailable(FallbackReason.UNSUPPORTED_DEVICE)
        retrieve.results = listOf(chunk("c1", "a.md", 0, 800, 0.9f))

        val summary = useCase("topic")!!

        assertThat(summary.engine).isEqualTo(SummaryEngine.CLOUD)
        assertThat(summary.fallbackReason).isEqualTo(FallbackReason.UNSUPPORTED_DEVICE)
        assertThat(cloud.lastText).isEqualTo("a".repeat(800))
    }

    @Test
    fun `a text too short for the on-device model goes to the cloud`() = runTest {
        retrieve.results = listOf(chunk("c1", "a.md", 0, 100, 0.9f))

        val summary = useCase("topic")!!

        assertThat(summary.engine).isEqualTo(SummaryEngine.CLOUD)
        assertThat(summary.fallbackReason).isEqualTo(FallbackReason.INPUT_TOO_SHORT)
    }

    @Test
    fun `the routing mode is passed through`() = runTest {
        retrieve.results = listOf(chunk("c1", "a.md", 0, 800, 0.9f))

        val summary = useCase("topic", RoutingMode.CLOUD_ONLY)!!

        assertThat(summary.engine).isEqualTo(SummaryEngine.CLOUD)
        assertThat(summary.fallbackReason).isNull()
        assertThat(onDevice.calls).isEqualTo(0)
    }

    @Test
    fun `a search failure is not swallowed`() = runTest {
        retrieve.error = IllegalStateException("network")

        val error = runCatching { useCase("topic") }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
    }
}
