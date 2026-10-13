package dev.juliamorozova.ragagent.presentation.query

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.juliamorozova.ragagent.domain.model.FallbackReason
import dev.juliamorozova.ragagent.domain.summary.OnDeviceAvailability
import dev.juliamorozova.ragagent.domain.summary.OnDeviceSummarizationException
import dev.juliamorozova.ragagent.domain.summary.OnDeviceSummarizer
import dev.juliamorozova.ragagent.domain.usecase.GenerateAnswerUseCase
import dev.juliamorozova.ragagent.domain.usecase.ProposeNotesFromChatUseCase
import dev.juliamorozova.ragagent.domain.usecase.SaveNoteUseCase
import dev.juliamorozova.ragagent.domain.usecase.SummarizeNotesUseCase
import dev.juliamorozova.ragagent.presentation.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * Maps a failure to text that is safe to show; never [Throwable.message], which can leak internals and host names.
 */
private fun Throwable.toUserMessage(): UiMessage = UiMessage(
    when {
        this is HttpException && code() == 429 -> R.string.query_error_rate_limited
        this is IOException -> R.string.query_error_timeout
        else -> R.string.query_error_generic
    },
)

private fun savedMessage(notes: List<String>): UiMessage =
    if (notes.size == 1) {
        UiMessage(R.string.query_saved_one, listOf(notes.single()))
    } else {
        UiMessage(R.string.query_saved_many, listOf(notes.size))
    }

private fun OnDeviceAvailability.toModelStatus(): ModelStatus = when (this) {
    OnDeviceAvailability.Available -> ModelStatus.Ready
    OnDeviceAvailability.Downloadable -> ModelStatus.Downloadable
    OnDeviceAvailability.Downloading -> ModelStatus.Downloading(percent = 0)
    is OnDeviceAvailability.Unavailable -> ModelStatus.Unavailable(reason)
}

private fun Throwable.toUnavailable(): ModelStatus.Unavailable =
    ModelStatus.Unavailable((this as? OnDeviceSummarizationException)?.reason ?: FallbackReason.UNKNOWN)

@HiltViewModel
class QueryViewModel @Inject constructor(
    private val generateAnswer: GenerateAnswerUseCase,
    private val proposeNotes: ProposeNotesFromChatUseCase,
    private val saveNote: SaveNoteUseCase,
    private val summarizeNotes: SummarizeNotesUseCase,
    private val onDeviceSummarizer: OnDeviceSummarizer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QueryUiState())
    val uiState: StateFlow<QueryUiState> = _uiState.asStateFlow()

    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onModeSelected(mode: QueryMode) {
        _uiState.update { it.copy(mode = mode, errorMessage = null) }
        if (mode == QueryMode.SUMMARIZE && _uiState.value.modelStatus == ModelStatus.Unknown) {
            viewModelScope.launch { loadModelStatus() }
        }
    }

    fun onSubmit() {
        when (_uiState.value.mode) {
            QueryMode.ASK -> ask()
            QueryMode.SUMMARIZE -> summarize()
        }
    }

    private fun ask() {
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

    private fun summarize() {
        val topic = _uiState.value.query
        if (topic.isBlank()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isSummarizing = true, errorMessage = null, summary = null, summaryNothingFound = false)
            }
            runCatching { summarizeNotes(topic) }
                .onSuccess { summary ->
                    _uiState.update {
                        it.copy(isSummarizing = false, summary = summary, summaryNothingFound = summary == null)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSummarizing = false, errorMessage = error.toUserMessage()) }
                }
        }
    }

    fun onDownloadModel() {
        if (_uiState.value.modelStatus != ModelStatus.Downloadable) return

        viewModelScope.launch {
            setModelStatus(ModelStatus.Downloading(percent = 0))
            try {
                onDeviceSummarizer.downloadModel { percent -> setModelStatus(ModelStatus.Downloading(percent)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setModelStatus(e.toUnavailable())
                return@launch
            }
            loadModelStatus()
        }
    }

    private suspend fun loadModelStatus() {
        setModelStatus(ModelStatus.Checking)
        val status = try {
            onDeviceSummarizer.availability().toModelStatus()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toUnavailable()
        }
        setModelStatus(status)
    }

    private fun setModelStatus(status: ModelStatus) {
        _uiState.update { it.copy(modelStatus = status) }
    }

    /**
     * Asks the agent what is worth saving; proposals wait in a dialog ([QueryUiState.pendingNotes]) until
     * [onConfirmSave].
     */
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
                            noteSaveMessage = if (notes.isEmpty()) UiMessage(R.string.query_nothing_to_save) else null,
                        )
                    }
                }
                .onFailure { error -> failSave(error) }
        }
    }

    fun onConfirmSave() {
        val notes = _uiState.value.pendingNotes
        if (notes.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingNote = true, pendingNotes = emptyList()) }
            runCatching { notes.forEach { saveNote(it) } }
                .onSuccess {
                    _uiState.update { it.copy(isSavingNote = false, noteSaveMessage = savedMessage(notes)) }
                }
                .onFailure { error -> failSave(error) }
        }
    }

    private fun failSave(error: Throwable) {
        _uiState.update {
            it.copy(
                isSavingNote = false,
                noteSaveNarration = null,
                noteSaveMessage = UiMessage(R.string.query_failed_to_save, listOf(error.toUserMessage())),
            )
        }
    }

    fun onDismissSave() {
        _uiState.update { it.copy(pendingNotes = emptyList()) }
    }

    fun onNoteSaveMessageShown() {
        _uiState.update { it.copy(noteSaveMessage = null) }
    }
}
