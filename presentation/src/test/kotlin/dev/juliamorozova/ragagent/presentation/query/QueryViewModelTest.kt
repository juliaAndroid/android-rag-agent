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
    private val saveNoteFromChat = FakeSaveNoteFromChatUseCase()
    private lateinit var viewModel: QueryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = QueryViewModel(generateAnswer, saveNoteFromChat)
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
}
