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
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/** Maps a raw network/HTTP failure to copy safe to show the user — never [Throwable.message],
 *  which can be a raw exception string (stack internals, host names). */
private fun Throwable.toUserMessage(): String = when {
    this is HttpException && code() == 429 -> "Rate limited, wait a moment and try again"
    this is IOException -> "Request timed out, try again"
    else -> "Something went wrong"
}

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
            _uiState.update { it.copy(isLoading = true, errorMessage = null, answerNarration = null) }
            runCatching {
                generateAnswer(query) { narration ->
                    _uiState.update { it.copy(answerNarration = narration) }
                }
            }
                .onSuccess { answer ->
                    _uiState.update {
                        it.copy(isLoading = false, answer = answer, answeredQuery = query, answerNarration = null)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.toUserMessage(), answerNarration = null)
                    }
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
                            noteSaveMessage = "Failed to save: ${error.toUserMessage()}",
                        )
                    }
                }
        }
    }

    fun onNoteSaveMessageShown() {
        _uiState.update { it.copy(noteSaveMessage = null) }
    }
}
