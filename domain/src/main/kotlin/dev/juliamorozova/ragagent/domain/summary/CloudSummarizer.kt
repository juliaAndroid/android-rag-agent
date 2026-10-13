package dev.juliamorozova.ragagent.domain.summary

interface CloudSummarizer {

    /** [topic] lets the cloud model focus; the on-device model takes text only. Returns bullets without markers. */
    suspend fun summarize(text: String, topic: String? = null): List<String>
}
