package dev.juliamorozova.ragagent.domain.summary

import dev.juliamorozova.ragagent.domain.model.FallbackReason

interface OnDeviceSummarizer {

    val minInputChars: Int

    val maxInputChars: Int

    suspend fun availability(): OnDeviceAvailability

    /**
     * Downloads the model when Downloadable; [onProgress] gets 0..100. Throws [OnDeviceSummarizationException] on
     * failure.
     */
    suspend fun downloadModel(onProgress: (percent: Int) -> Unit = {})

    /** Returns bullets without markers; throws [OnDeviceSummarizationException] when the model can't summarize. */
    suspend fun summarize(text: String): List<String>
}

sealed interface OnDeviceAvailability {
    data object Available : OnDeviceAvailability
    data object Downloadable : OnDeviceAvailability
    data object Downloading : OnDeviceAvailability
    data class Unavailable(val reason: FallbackReason) : OnDeviceAvailability
}

class OnDeviceSummarizationException(
    val reason: FallbackReason,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: reason.name, cause)
