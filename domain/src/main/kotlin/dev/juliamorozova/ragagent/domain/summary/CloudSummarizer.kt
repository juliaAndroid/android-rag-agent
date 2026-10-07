package dev.juliamorozova.ragagent.domain.summary

/** Summarizes text with a cloud model (Claude via the proxy). Works on every device. */
interface CloudSummarizer {

    /** @return The summary as separate bullet points, without bullet markers. */
    suspend fun summarize(text: String): List<String>
}
