package dev.juliamorozova.ragagent.presentation.query

import dev.juliamorozova.ragagent.domain.model.FallbackReason
import dev.juliamorozova.ragagent.domain.model.NotesSummary
import dev.juliamorozova.ragagent.domain.model.RagAnswer

enum class QueryMode { ASK, SUMMARIZE }

sealed interface ModelStatus {
    data object Unknown : ModelStatus
    data object Checking : ModelStatus
    data object Ready : ModelStatus
    data object Downloadable : ModelStatus
    data class Downloading(val percent: Int) : ModelStatus
    data class Unavailable(val reason: FallbackReason) : ModelStatus
}

data class QueryUiState(
    val mode: QueryMode = QueryMode.ASK,
    val query: String = "",
    val isLoading: Boolean = false,
    val answerNarration: String? = null,
    val answer: RagAnswer? = null,
    /** The query that produced [answer]; [query] tracks live edits and may have changed since. */
    val answeredQuery: String? = null,
    val errorMessage: UiMessage? = null,
    val isSavingNote: Boolean = false,
    val noteSaveNarration: String? = null,
    /** Notes proposed by the agent, awaiting confirmation; nothing is stored until [QueryViewModel.onConfirmSave]. */
    val pendingNotes: List<String> = emptyList(),
    /** One-shot event: shown in a Snackbar, then [QueryViewModel.onNoteSaveMessageShown]. */
    val noteSaveMessage: UiMessage? = null,
    val isSummarizing: Boolean = false,
    val summary: NotesSummary? = null,
    val summaryNothingFound: Boolean = false,
    val modelStatus: ModelStatus = ModelStatus.Unknown,
)
