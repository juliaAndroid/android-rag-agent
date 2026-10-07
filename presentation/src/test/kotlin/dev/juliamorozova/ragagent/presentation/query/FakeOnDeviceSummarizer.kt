package dev.juliamorozova.ragagent.presentation.query

import dev.juliamorozova.ragagent.domain.model.FallbackReason
import dev.juliamorozova.ragagent.domain.summary.OnDeviceAvailability
import dev.juliamorozova.ragagent.domain.summary.OnDeviceSummarizationException
import dev.juliamorozova.ragagent.domain.summary.OnDeviceSummarizer

class FakeOnDeviceSummarizer : OnDeviceSummarizer {
    override val minInputChars = 400
    override val maxInputChars = 7_860

    var availability: OnDeviceAvailability = OnDeviceAvailability.Available
    var availabilityError: Exception? = null

    /** Progress values reported by [downloadModel], in order. */
    var downloadSteps: List<Int> = listOf(0, 50, 100)
    var downloadFails = false

    /** What [availability] becomes once a download succeeds. */
    var availabilityAfterDownload: OnDeviceAvailability = OnDeviceAvailability.Available

    var availabilityCalls = 0
        private set
    var downloadCalls = 0
        private set

    override suspend fun availability(): OnDeviceAvailability {
        availabilityCalls++
        availabilityError?.let { throw it }
        return availability
    }

    override suspend fun downloadModel(onProgress: (percent: Int) -> Unit) {
        downloadCalls++
        downloadSteps.forEach(onProgress)
        if (downloadFails) throw OnDeviceSummarizationException(FallbackReason.NOT_ENOUGH_DISK_SPACE)
        availability = availabilityAfterDownload
    }

    override suspend fun summarize(text: String): List<String> = error("Not used by the ViewModel")
}
