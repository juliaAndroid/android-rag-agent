package dev.juliamorozova.ragagent.domain.summary

import dev.juliamorozova.ragagent.domain.model.FallbackReason

/** Summarizes text with a model that runs on the phone (e.g. Gemini Nano). */
interface OnDeviceSummarizer {

    /** Shortest input the model accepts; shorter text must go to the cloud. */
    val minInputChars: Int

    /** Longest input the model accepts; callers trim to fit before asking. */
    val maxInputChars: Int

    suspend fun availability(): OnDeviceAvailability

    /**
     * @return The summary as separate bullet points, without bullet markers.
     * @throws OnDeviceSummarizationException when the model can't produce a summary; its
     * [OnDeviceSummarizationException.reason] says why.
     */
    suspend fun summarize(text: String): List<String>
}

/** Whether the on-device model can be used right now. */
sealed interface OnDeviceAvailability {
    data object Available : OnDeviceAvailability
    data object Downloadable : OnDeviceAvailability
    data object Downloading : OnDeviceAvailability
    data class Unavailable(val reason: FallbackReason) : OnDeviceAvailability
}

/** The on-device model couldn't summarize; [reason] drives whether and how to fall back. */
class OnDeviceSummarizationException(
    val reason: FallbackReason,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: reason.name, cause)
