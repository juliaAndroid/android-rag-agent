package dev.juliamorozova.ragagent.domain.summary

import com.google.common.truth.Truth.assertThat
import dev.juliamorozova.ragagent.domain.model.Chunk
import dev.juliamorozova.ragagent.domain.model.FallbackReason
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
import dev.juliamorozova.ragagent.domain.model.SummaryEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class SummaryRouterTest {

    private class FakeOnDevice(
        override val minInputChars: Int = 400,
        override val maxInputChars: Int = 7_860,
    ) : OnDeviceSummarizer {
        var availability: OnDeviceAvailability = OnDeviceAvailability.Available
        var availabilityError: Exception? = null

        /** One outcome per call, in order; the last one repeats. */
        var outcomes: List<() -> List<String>> = listOf { listOf("on-device bullet") }

        var availabilityCalls = 0
        var summarizeCalls = 0

        override suspend fun availability(): OnDeviceAvailability {
            availabilityCalls++
            availabilityError?.let { throw it }
            return availability
        }

        override suspend fun summarize(text: String): List<String> {
            val outcome = outcomes[minOf(summarizeCalls, outcomes.lastIndex)]
            summarizeCalls++
            return outcome()
        }

        fun failWith(reason: FallbackReason) {
            outcomes = listOf { throw OnDeviceSummarizationException(reason) }
        }
    }

    private class FakeCloud : CloudSummarizer {
        var calls = 0
        override suspend fun summarize(text: String): List<String> {
            calls++
            return listOf("cloud bullet")
        }
    }

    private val onDevice = FakeOnDevice()
    private val cloud = FakeCloud()
    private val router = SummaryRouter(onDevice, cloud, busyRetryDelaysMillis = listOf(1L, 1L))

    private val text = "x".repeat(1_000)
    private val sources = listOf(RetrievedChunk(Chunk("c1", "doc.md", "text", position = 0), score = 0.9f))

    @Test
    fun `an available on-device model summarizes and the cloud is not called`() = runTest {
        val result = router.summarize(text, sources)

        assertThat(result.engine).isEqualTo(SummaryEngine.ON_DEVICE)
        assertThat(result.bullets).containsExactly("on-device bullet")
        assertThat(result.fallbackReason).isNull()
        assertThat(cloud.calls).isEqualTo(0)
    }

    @Test
    fun `sources are passed through to the result`() = runTest {
        assertThat(router.summarize(text, sources).sources).isEqualTo(sources)
    }

    @Test
    fun `every failure reason except BUSY falls back to the cloud with that reason`() = runTest {
        for (reason in FallbackReason.entries.filter { it != FallbackReason.BUSY }) {
            val freshOnDevice = FakeOnDevice().apply { failWith(reason) }
            val freshCloud = FakeCloud()

            val result = SummaryRouter(freshOnDevice, freshCloud).summarize(text, sources)

            assertThat(result.engine).isEqualTo(SummaryEngine.CLOUD)
            assertThat(result.fallbackReason).isEqualTo(reason)
            assertThat(freshCloud.calls).isEqualTo(1)
        }
    }

    @Test
    fun `an unavailable model falls back without trying to summarize`() = runTest {
        onDevice.availability = OnDeviceAvailability.Unavailable(FallbackReason.SERVICE_UNAVAILABLE)

        val result = router.summarize(text, sources)

        assertThat(result.fallbackReason).isEqualTo(FallbackReason.SERVICE_UNAVAILABLE)
        assertThat(onDevice.summarizeCalls).isEqualTo(0)
    }

    @Test
    fun `a model that still has to be downloaded falls back`() = runTest {
        onDevice.availability = OnDeviceAvailability.Downloadable

        val result = router.summarize(text, sources)

        assertThat(result.fallbackReason).isEqualTo(FallbackReason.MODEL_NOT_DOWNLOADED)
        assertThat(onDevice.summarizeCalls).isEqualTo(0)
    }

    @Test
    fun `a model that is downloading falls back`() = runTest {
        onDevice.availability = OnDeviceAvailability.Downloading

        val result = router.summarize(text, sources)

        assertThat(result.fallbackReason).isEqualTo(FallbackReason.MODEL_DOWNLOADING)
        assertThat(onDevice.summarizeCalls).isEqualTo(0)
    }

    @Test
    fun `input shorter than the minimum goes to the cloud without calling the model`() = runTest {
        val result = router.summarize("x".repeat(399), sources)

        assertThat(result.fallbackReason).isEqualTo(FallbackReason.INPUT_TOO_SHORT)
        assertThat(onDevice.summarizeCalls).isEqualTo(0)
    }

    @Test
    fun `input longer than the maximum goes to the cloud without calling the model`() = runTest {
        val result = router.summarize("x".repeat(7_861), sources)

        assertThat(result.fallbackReason).isEqualTo(FallbackReason.INPUT_TOO_LARGE)
        assertThat(onDevice.summarizeCalls).isEqualTo(0)
    }

    @Test
    fun `input exactly at the limits is accepted`() = runTest {
        assertThat(router.summarize("x".repeat(400), sources).engine).isEqualTo(SummaryEngine.ON_DEVICE)
        assertThat(router.summarize("x".repeat(7_860), sources).engine).isEqualTo(SummaryEngine.ON_DEVICE)
    }

    @Test
    fun `BUSY is retried and a later success stays on-device`() = runTest {
        var calls = 0
        onDevice.outcomes = listOf {
            if (calls++ < 1) throw OnDeviceSummarizationException(FallbackReason.BUSY) else listOf("ok")
        }

        val result = router.summarize(text, sources)

        assertThat(result.engine).isEqualTo(SummaryEngine.ON_DEVICE)
        assertThat(onDevice.summarizeCalls).isEqualTo(2)
        assertThat(cloud.calls).isEqualTo(0)
    }

    @Test
    fun `BUSY that never clears falls back after the retries run out`() = runTest {
        onDevice.failWith(FallbackReason.BUSY)

        val result = router.summarize(text, sources)

        assertThat(result.engine).isEqualTo(SummaryEngine.CLOUD)
        assertThat(result.fallbackReason).isEqualTo(FallbackReason.BUSY)
        assertThat(onDevice.summarizeCalls).isEqualTo(3) // first try + two retries
    }

    @Test
    fun `an unsupported device is remembered and the on-device model is skipped afterwards`() = runTest {
        onDevice.availability = OnDeviceAvailability.Unavailable(FallbackReason.UNSUPPORTED_DEVICE)

        router.summarize(text, sources)
        val second = router.summarize(text, sources)

        assertThat(second.fallbackReason).isEqualTo(FallbackReason.UNSUPPORTED_DEVICE)
        assertThat(onDevice.availabilityCalls).isEqualTo(1)
    }

    @Test
    fun `a fixable problem is not remembered and the model is tried again next time`() = runTest {
        onDevice.availability = OnDeviceAvailability.Unavailable(FallbackReason.SERVICE_UNAVAILABLE)

        router.summarize(text, sources)
        onDevice.availability = OnDeviceAvailability.Available
        val second = router.summarize(text, sources)

        assertThat(second.engine).isEqualTo(SummaryEngine.ON_DEVICE)
        assertThat(onDevice.availabilityCalls).isEqualTo(2)
    }

    @Test
    fun `an unexpected exception from the model is treated as UNKNOWN and falls back`() = runTest {
        onDevice.outcomes = listOf { throw IllegalStateException("boom") }

        val result = router.summarize(text, sources)

        assertThat(result.engine).isEqualTo(SummaryEngine.CLOUD)
        assertThat(result.fallbackReason).isEqualTo(FallbackReason.UNKNOWN)
    }

    @Test
    fun `an exception while checking availability falls back`() = runTest {
        onDevice.availabilityError = OnDeviceSummarizationException(FallbackReason.SERVICE_UNAVAILABLE)

        val result = router.summarize(text, sources)

        assertThat(result.fallbackReason).isEqualTo(FallbackReason.SERVICE_UNAVAILABLE)
        assertThat(onDevice.summarizeCalls).isEqualTo(0)
    }

    @Test
    fun `CLOUD_ONLY skips the on-device model entirely`() = runTest {
        val result = router.summarize(text, sources, RoutingMode.CLOUD_ONLY)

        assertThat(result.engine).isEqualTo(SummaryEngine.CLOUD)
        assertThat(result.fallbackReason).isNull()
        assertThat(onDevice.availabilityCalls).isEqualTo(0)
    }

    @Test
    fun `ON_DEVICE_ONLY reports the failure instead of falling back`() = runTest {
        onDevice.failWith(FallbackReason.GENERATION_FAILED)

        val error = assertThrows(OnDeviceSummarizationException::class.java) {
            kotlinx.coroutines.runBlocking { router.summarize(text, sources, RoutingMode.ON_DEVICE_ONLY) }
        }

        assertThat(error.reason).isEqualTo(FallbackReason.GENERATION_FAILED)
        assertThat(cloud.calls).isEqualTo(0)
    }
}
