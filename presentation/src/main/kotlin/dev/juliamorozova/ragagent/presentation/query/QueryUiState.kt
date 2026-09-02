package dev.juliamorozova.ragagent.presentation.query

import dev.juliamorozova.ragagent.domain.model.RagAnswer

/** UI state for the single query/response screen. */
data class QueryUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val answer: RagAnswer? = null,
    val errorMessage: String? = null,
)
