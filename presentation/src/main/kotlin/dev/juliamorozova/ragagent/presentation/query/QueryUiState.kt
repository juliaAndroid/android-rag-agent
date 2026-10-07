package dev.juliamorozova.ragagent.presentation.query

import dev.juliamorozova.ragagent.domain.model.FallbackReason
import dev.juliamorozova.ragagent.domain.model.NotesSummary
import dev.juliamorozova.ragagent.domain.model.RagAnswer

/** What the input field and the action button do. */
enum class QueryMode { ASK, SUMMARIZE }

/** State of the on-device model, shown under a summary. */
sealed interface ModelStatus {
    /** Not looked at yet (the status is checked the first time Summarize is opened). */
    data object Unknown : ModelStatus
    data object Checking : ModelStatus
    data object Ready : ModelStatus
    data object Downloadable : ModelStatus
    data class Downloading(val percent: Int) : ModelStatus
    data class Unavailable(val reason: FallbackReason) : ModelStatus
}

/** UI state for the single query/response screen. */
data class QueryUiState(
    val mode: QueryMode = QueryMode.ASK,
    val query: String = "",
    val isLoading: Boolean = false,
    /** Claude's own commentary while an answer is being generated (e.g. "I'll check your
     *  personal knowledge base..."), shown in place of a generic loading label. */
    val answerNarration: String? = null,
    val answer: RagAnswer? = null,
    /** The query that produced [answer] — distinct from [query], which tracks live edits
     *  to the input field and may have changed since that answer was generated. */
    val answeredQuery: String? = null,
    val errorMessage: UiMessage? = null,
    val isSavingNote: Boolean = false,
    /** Claude's own commentary while a note is being proposed — shown next to the spinner. */
    val noteSaveNarration: String? = null,
    /** Notes the agent proposed, awaiting the user's confirmation in a dialog. Nothing is
     *  stored until [QueryViewModel.onConfirmSave]; non-empty means the dialog is showing. */
    val pendingNotes: List<String> = emptyList(),
    /** One-shot event: UI shows it in a Snackbar, then calls [QueryViewModel.onNoteSaveMessageShown]. */
    val noteSaveMessage: UiMessage? = null,
    val isSummarizing: Boolean = false,
    val summary: NotesSummary? = null,
    /** The last Summarize found no notes for its topic. */
    val summaryNothingFound: Boolean = false,
    val modelStatus: ModelStatus = ModelStatus.Unknown,
)
