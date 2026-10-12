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
        var lastTopic: String? = null
        override suspend fun summarize(text: String, topic: String?): List<String> {
            lastText = text
            lastTopic = topic
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
    fun `chunks that all score below the relevance floor return null without calling any model`() = runTest {
        retrieve.results = listOf(
            chunk("a", "note.md", 0, length = 500, score = MIN_RELEVANCE - 0.01f),
            chunk("b", "note.md", 1, length = 500, score = 0.1f),
        )

        assertThat(useCase("i like london")).isNull()
        assertThat(onDevice.calls).isEqualTo(0)
        assertThat(cloud.lastText).isNull()
    }

    @Test
    fun `a best score exactly at the relevance floor is still summarized`() = runTest {
        retrieve.results = listOf(chunk("a", "note.md", 0, length = 500, score = MIN_RELEVANCE))

        assertThat(useCase("clean architecture")).isNotNull()
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
    fun `only the chunks of the note with the best-scoring chunk are used`() = runTest {
        retrieve.results = listOf(
            chunk("a1", "a.md", 0, 500, 0.9f),
            chunk("b1", "b.md", 0, 500, 0.8f), // second-best overall, but another note
            chunk("a2", "a.md", 1, 500, 0.5f),
            chunk("c1", "c.md", 0, 500, 0.4f),
        )

        val summary = useCase("topic")!!

        assertThat(summary.sources.map { it.chunk.id }).containsExactly("a1", "a2").inOrder()
    }

    @Test
    fun `the chosen note's chunks are read in position order, not in score order`() = runTest {
        retrieve.results = listOf(
            chunk("p2", "a.md", 2, 500, 0.9f, fill = 'C'),
            chunk("p0", "a.md", 0, 500, 0.8f, fill = 'A'),
            chunk("p1", "a.md", 1, 500, 0.7f, fill = 'B'),
        )

        val summary = useCase("topic")!!

        assertThat(summary.sources.map { it.chunk.id }).containsExactly("p0", "p1", "p2").inOrder()
        assertThat(onDevice.lastText).isEqualTo(
            "A".repeat(500) + "\n\n" + "B".repeat(500) + "\n\n" + "C".repeat(500),
        )
    }

    @Test
    fun `at most five chunks are used, the best ones`() = runTest {
        retrieve.results = (0 until 7).map { chunk("c$it", "a.md", it, 400, score = 1f - it * 0.1f) }

        val summary = useCase("topic")!!

        assertThat(summary.sources.map { it.chunk.id }).containsExactly("c0", "c1", "c2", "c3", "c4").inOrder()
    }

    @Test
    fun `only the best chunks of the note that fit the on-device limit are kept`() = runTest {
        // Four 3000-char chunks do not fit 7860; the two best do (3000 + 2 + 3000).
        retrieve.results = listOf(
            chunk("low", "a.md", 0, 3_000, 0.2f),
            chunk("best", "a.md", 1, 3_000, 0.9f),
            chunk("mid", "a.md", 2, 3_000, 0.5f),
            chunk("lowest", "a.md", 3, 3_000, 0.1f),
        )

        val summary = useCase("topic")!!

        assertThat(summary.sources.map { it.chunk.id }).containsExactly("best", "mid").inOrder()
        assertThat(summary.engine).isEqualTo(SummaryEngine.ON_DEVICE)
    }

    @Test
    fun `a smaller lower-ranked chunk still fits after a large one is skipped`() = runTest {
        retrieve.results = listOf(
            chunk("big1", "a.md", 0, 4_000, 0.9f),
            chunk("big2", "a.md", 1, 4_000, 0.8f),
            chunk("small", "a.md", 2, 500, 0.1f),
        )

        val summary = useCase("topic")!!

        assertThat(summary.sources.map { it.chunk.id }).containsExactly("big1", "small").inOrder()
    }

    @Test
    fun `the topic is passed on to the summarizer when summarizing a topic`() = runTest {
        onDevice.availability = OnDeviceAvailability.Unavailable(FallbackReason.UNSUPPORTED_DEVICE)
        retrieve.results = listOf(chunk("c1", "a.md", 0, 800, 0.9f))

        useCase("  clean architecture  ")

        assertThat(cloud.lastTopic).isEqualTo("clean architecture")
    }

    @Test
    fun `summarize passes the given topic to the cloud summarizer`() = runTest {
        onDevice.availability = OnDeviceAvailability.Unavailable(FallbackReason.UNSUPPORTED_DEVICE)

        useCase.summarize(listOf(chunk("c1", "a.md", 0, 800, 0.9f)), topic = "kmm")

        assertThat(cloud.lastTopic).isEqualTo("kmm")
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
    fun `selectChunks only selects and does not summarize`() = runTest {
        retrieve.results = listOf(chunk("c1", "a.md", 0, 800, 0.9f))

        val selected = useCase.selectChunks("topic")

        assertThat(selected.map { it.chunk.id }).containsExactly("c1")
        assertThat(onDevice.calls).isEqualTo(0)
        assertThat(cloud.lastText).isNull()
    }

    @Test
    fun `selectChunks of a blank topic is empty and does not search`() = runTest {
        assertThat(useCase.selectChunks("  ")).isEmpty()
        assertThat(retrieve.lastQuery).isNull()
    }

    @Test
    fun `summarize uses exactly the given chunks in the given order and does not search`() = runTest {
        val given = listOf(
            chunk("z", "z.md", 0, 500, 0.1f, fill = 'Z'),
            chunk("a", "a.md", 0, 500, 0.9f, fill = 'A'),
        ) // deliberately two notes and a "wrong" order: summarize() must not reselect or reorder

        val summary = useCase.summarize(given)!!

        assertThat(retrieve.lastQuery).isNull()
        assertThat(onDevice.lastText).isEqualTo("Z".repeat(500) + "\n\n" + "A".repeat(500))
        assertThat(summary.sources).isEqualTo(given)
    }

    @Test
    fun `summarize of no chunks returns null without calling a model`() = runTest {
        assertThat(useCase.summarize(emptyList())).isNull()
        assertThat(onDevice.calls).isEqualTo(0)
    }

    @Test
    fun `the same chunks can be summarized by both engines`() = runTest {
        val given = listOf(chunk("c1", "a.md", 0, 800, 0.9f))

        val cloudSummary = useCase.summarize(given, RoutingMode.CLOUD_ONLY)!!
        val nanoSummary = useCase.summarize(given, RoutingMode.ON_DEVICE_ONLY)!!

        assertThat(cloudSummary.engine).isEqualTo(SummaryEngine.CLOUD)
        assertThat(nanoSummary.engine).isEqualTo(SummaryEngine.ON_DEVICE)
        assertThat(cloudSummary.sources).isEqualTo(nanoSummary.sources)
    }

    @Test
    fun `a search failure is not swallowed`() = runTest {
        retrieve.error = IllegalStateException("network")

        val error = runCatching { useCase("topic") }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
    }
}
