package dev.juliamorozova.ragagent.presentation.query

import com.google.common.truth.Truth.assertThat
import dev.juliamorozova.ragagent.domain.model.RagAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val generateAnswer = FakeGenerateAnswerUseCase()
    private val proposeNotes = FakeProposeNotesFromChatUseCase()
    private val saveNote = FakeSaveNoteUseCase()
    private lateinit var viewModel: QueryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = QueryViewModel(generateAnswer, proposeNotes, saveNote)
    }

    /** Leaves the ViewModel with a finished answer, so "Save as note" has something to work on. */
    private fun answerAQuestion() {
        viewModel.onQueryChanged("my name is Julia")
        viewModel.onSubmit()
        generateAnswer.answer.complete(RagAnswer(text = "Nice to meet you", citations = emptyList()))
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onSubmit success sets isLoading true then false with the answer`() = runTest(testDispatcher) {
        viewModel.onQueryChanged("what is the answer")

        viewModel.onSubmit()
        testDispatcher.scheduler.runCurrent()
        assertThat(viewModel.uiState.value.isLoading).isTrue()

        generateAnswer.answer.complete(RagAnswer(text = "42", citations = emptyList()))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.answer?.text).isEqualTo("42")
        assertThat(state.answeredQuery).isEqualTo("what is the answer")
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun `onSubmit failure surfaces a user-facing error message`() = runTest(testDispatcher) {
        generateAnswer.shouldFail = true
        viewModel.onQueryChanged("what is the answer")

        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNotNull()
        assertThat(state.answer).isNull()
    }

    @Test
    fun `blank query is a no-op`() = runTest(testDispatcher) {
        viewModel.onQueryChanged("   ")

        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.answer).isNull()
        assertThat(generateAnswer.lastQuery).isNull()
    }

    @Test
    fun `saving with no answer yet does nothing`() = runTest(testDispatcher) {
        viewModel.saveAsNote()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.pendingNotes).isEmpty()
        assertThat(viewModel.uiState.value.noteSaveMessage).isNull()
    }

    @Test
    fun `save as note shows the proposal for confirmation and stores nothing yet`() = runTest(testDispatcher) {
        proposeNotes.notes = listOf("Your name is Julia.")
        answerAQuestion()

        viewModel.saveAsNote()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.pendingNotes).containsExactly("Your name is Julia.")
        assertThat(state.isSavingNote).isFalse()
        assertThat(saveNote.saved).isEmpty()
    }

    @Test
    fun `confirming stores exactly the proposed notes and reports what was saved`() = runTest(testDispatcher) {
        proposeNotes.notes = listOf("Your name is Julia.")
        answerAQuestion()
        viewModel.saveAsNote()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onConfirmSave()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(saveNote.saved).containsExactly("Your name is Julia.")
        assertThat(state.pendingNotes).isEmpty()
        assertThat(state.noteSaveMessage).isEqualTo("Saved: Your name is Julia.")
    }

    @Test
    fun `confirming several notes reports a count`() = runTest(testDispatcher) {
        proposeNotes.notes = listOf("Your name is Julia.", "You like Kotlin.")
        answerAQuestion()
        viewModel.saveAsNote()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onConfirmSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(saveNote.saved).hasSize(2)
        assertThat(viewModel.uiState.value.noteSaveMessage).isEqualTo("Saved 2 notes")
    }

    @Test
    fun `cancelling stores nothing`() = runTest(testDispatcher) {
        proposeNotes.notes = listOf("Your name is Julia.")
        answerAQuestion()
        viewModel.saveAsNote()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDismissSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.pendingNotes).isEmpty()
        assertThat(saveNote.saved).isEmpty()
    }

    @Test
    fun `nothing worth saving shows a message and no dialog`() = runTest(testDispatcher) {
        proposeNotes.notes = emptyList()
        answerAQuestion()

        viewModel.saveAsNote()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.pendingNotes).isEmpty()
        assertThat(state.noteSaveMessage).isEqualTo("Nothing new to save")
    }

    @Test
    fun `a failed proposal surfaces an error message`() = runTest(testDispatcher) {
        proposeNotes.shouldFail = true
        answerAQuestion()

        viewModel.saveAsNote()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.noteSaveMessage).startsWith("Failed to save")
        assertThat(state.pendingNotes).isEmpty()
        assertThat(state.isSavingNote).isFalse()
    }

    @Test
    fun `a failed write after confirming surfaces an error message`() = runTest(testDispatcher) {
        proposeNotes.notes = listOf("Your name is Julia.")
        saveNote.shouldFail = true
        answerAQuestion()
        viewModel.saveAsNote()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onConfirmSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.noteSaveMessage).startsWith("Failed to save")
        assertThat(viewModel.uiState.value.isSavingNote).isFalse()
    }
}
