package dev.juliamorozova.ragagent.domain.summary

/** Summarizes text with a cloud model (Claude via the proxy). Works on every device. */
interface CloudSummarizer {

    /**
     * @param topic What the user asked about. A cloud model can be told to focus on it, unlike the
     * on-device model, which only takes the text.
     * @return The summary as separate bullet points, without bullet markers.
     */
    suspend fun summarize(text: String, topic: String? = null): List<String>
}
