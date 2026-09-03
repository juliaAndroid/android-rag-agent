package dev.juliamorozova.ragagent.presentation.query

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.juliamorozova.ragagent.domain.usecase.GenerateAnswerUseCase
import dev.juliamorozova.ragagent.domain.usecase.SaveNoteFromChatUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueryViewModel @Inject constructor(
    private val generateAnswer: GenerateAnswerUseCase,
    private val saveNoteFromChat: SaveNoteFromChatUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QueryUiState())
    val uiState: StateFlow<QueryUiState> = _uiState.asStateFlow()

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onSubmit() {
        val query = _uiState.value.query
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { generateAnswer(query) }
                .onSuccess { answer ->
                    _uiState.update { it.copy(isLoading = false, answer = answer, answeredQuery = query) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Unknown error") }
                }
        }
    }

    fun saveAsNote() {
        val query = _uiState.value.answeredQuery ?: return
        val answer = _uiState.value.answer ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingNote = true, noteSaveNarration = null) }
            runCatching {
                saveNoteFromChat(query, answer.text) { narration ->
                    _uiState.update { it.copy(noteSaveNarration = narration) }
                }
            }
                .onSuccess { closingText ->
                    _uiState.update {
                        it.copy(
                            isSavingNote = false,
                            noteSaveNarration = null,
                            noteSaveMessage = closingText.ifBlank { "Saved" },
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSavingNote = false,
                            noteSaveNarration = null,
                            noteSaveMessage = "Failed to save: ${error.message}",
                        )
                    }
                }
        }
    }

    fun onNoteSaveMessageShown() {
        _uiState.update { it.copy(noteSaveMessage = null) }
    }
}
