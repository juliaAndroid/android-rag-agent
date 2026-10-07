package dev.juliamorozova.ragagent.domain.model

/** Who produced a summary, so the UI can badge it. */
enum class SummaryEngine { ON_DEVICE, CLOUD }

/** Why the on-device model could not be used and the summary came from the cloud instead. */
enum class FallbackReason {
    /** The device or its system image doesn't offer the on-device model at all. Won't change this session. */
    UNSUPPORTED_DEVICE,

    /** The system AI service is installed but couldn't be reached (e.g. disabled by the user). */
    SERVICE_UNAVAILABLE,
    MODEL_NOT_DOWNLOADED,
    MODEL_DOWNLOADING,
    INPUT_TOO_SHORT,
    INPUT_TOO_LARGE,

    /** The model ran but refused or failed to produce a usable answer for this input. */
    GENERATION_FAILED,
    BUSY,
    NEEDS_SYSTEM_UPDATE,
    NOT_ENOUGH_DISK_SPACE,
    UNKNOWN,
}

/**
 * A short summary of the notes on a topic.
 *
 * @property fallbackReason Set only when [engine] is [SummaryEngine.CLOUD] because the
 * on-device model was tried or ruled out first; null when the cloud was chosen directly.
 * @property sources The retrieved chunks the summary was built from, for citation display.
 * @property durationMillis How long the summarizer that produced [bullets] took, excluding retrieval
 * and any failed on-device attempt before a fallback.
 */
data class NotesSummary(
    val bullets: List<String>,
    val engine: SummaryEngine,
    val fallbackReason: FallbackReason? = null,
    val sources: List<RetrievedChunk> = emptyList(),
    val durationMillis: Long = 0,
)
