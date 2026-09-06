package dev.juliamorozova.ragagent.presentation.query

import dev.juliamorozova.ragagent.domain.model.RagAnswer

/** UI state for the single query/response screen. */
data class QueryUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    /** Claude's own commentary while an answer is being generated (e.g. "I'll check your
     *  personal knowledge base..."), shown in place of a generic loading label. */
    val answerNarration: String? = null,
    val answer: RagAnswer? = null,
    /** The query that produced [answer] — distinct from [query], which tracks live edits
     *  to the input field and may have changed since that answer was generated. */
    val answeredQuery: String? = null,
    val errorMessage: String? = null,
    val isSavingNote: Boolean = false,
    /** Claude's own commentary while a save is in progress — shown next to the spinner. */
    val noteSaveNarration: String? = null,
    /** One-shot event: UI shows it in a Snackbar, then calls [QueryViewModel.onNoteSaveMessageShown]. */
    val noteSaveMessage: String? = null,
)
