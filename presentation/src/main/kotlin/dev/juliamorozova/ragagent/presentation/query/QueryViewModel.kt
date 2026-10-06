package dev.juliamorozova.ragagent.presentation.query

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.juliamorozova.ragagent.domain.usecase.GenerateAnswerUseCase
import dev.juliamorozova.ragagent.domain.usecase.ProposeNotesFromChatUseCase
import dev.juliamorozova.ragagent.domain.usecase.SaveNoteUseCase
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

private const val NOTHING_TO_SAVE_MESSAGE = "Nothing new to save"

private fun savedMessage(notes: List<String>): String =
    if (notes.size == 1) "Saved: ${notes.single()}" else "Saved ${notes.size} notes"

@HiltViewModel
class QueryViewModel @Inject constructor(
    private val generateAnswer: GenerateAnswerUseCase,
    private val proposeNotes: ProposeNotesFromChatUseCase,
    private val saveNote: SaveNoteUseCase,
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

    /** Asks the agent what is worth saving. Nothing is stored yet: the proposals go to a
     *  confirmation dialog ([QueryUiState.pendingNotes]) and are stored only by [onConfirmSave]. */
    fun saveAsNote() {
        val query = _uiState.value.answeredQuery ?: return
        val answer = _uiState.value.answer ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingNote = true, noteSaveNarration = null) }
            runCatching {
                proposeNotes(query, answer.text) { narration ->
                    _uiState.update { it.copy(noteSaveNarration = narration) }
                }
            }
                .onSuccess { notes ->
                    _uiState.update {
                        it.copy(
                            isSavingNote = false,
                            noteSaveNarration = null,
                            pendingNotes = notes,
                            noteSaveMessage = if (notes.isEmpty()) NOTHING_TO_SAVE_MESSAGE else null,
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

    /** Stores exactly the notes the user just saw in the dialog. */
    fun onConfirmSave() {
        val notes = _uiState.value.pendingNotes
        if (notes.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingNote = true, pendingNotes = emptyList()) }
            runCatching { notes.forEach { saveNote(it) } }
                .onSuccess {
                    _uiState.update { it.copy(isSavingNote = false, noteSaveMessage = savedMessage(notes)) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSavingNote = false,
                            noteSaveMessage = "Failed to save: ${error.toUserMessage()}",
                        )
                    }
                }
        }
    }

    /** The user declined: drop the proposals without storing anything. */
    fun onDismissSave() {
        _uiState.update { it.copy(pendingNotes = emptyList()) }
    }

    fun onNoteSaveMessageShown() {
        _uiState.update { it.copy(noteSaveMessage = null) }
    }
}
