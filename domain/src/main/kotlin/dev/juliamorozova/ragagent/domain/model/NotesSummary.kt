package dev.juliamorozova.ragagent.domain.model

enum class SummaryEngine { ON_DEVICE, CLOUD }

enum class FallbackReason {
    UNSUPPORTED_DEVICE,

    SERVICE_UNAVAILABLE,
    MODEL_NOT_DOWNLOADED,
    MODEL_DOWNLOADING,
    INPUT_TOO_SHORT,
    INPUT_TOO_LARGE,

    GENERATION_FAILED,
    BUSY,
    NEEDS_SYSTEM_UPDATE,
    NOT_ENOUGH_DISK_SPACE,
    UNKNOWN,
}

/**
 * Summary of the notes on a topic. [fallbackReason]: why the cloud was used; [durationMillis]: the summarizer
 * only, without retrieval or a failed on-device try.
 */
data class NotesSummary(
    val bullets: List<String>,
    val engine: SummaryEngine,
    val fallbackReason: FallbackReason? = null,
    val sources: List<RetrievedChunk> = emptyList(),
    val durationMillis: Long = 0,
)
