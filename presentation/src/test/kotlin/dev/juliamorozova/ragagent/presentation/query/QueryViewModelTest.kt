package dev.juliamorozova.ragagent.presentation.query

import com.google.common.truth.Truth.assertThat
import dev.juliamorozova.ragagent.domain.model.FallbackReason
import dev.juliamorozova.ragagent.domain.model.NotesSummary
import dev.juliamorozova.ragagent.domain.model.RagAnswer
import dev.juliamorozova.ragagent.domain.model.SummaryEngine
import dev.juliamorozova.ragagent.domain.summary.OnDeviceAvailability
import dev.juliamorozova.ragagent.domain.summary.OnDeviceSummarizationException
import dev.juliamorozova.ragagent.presentation.R
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
    private val summarizeNotes = FakeSummarizeNotesUseCase()
    private val onDevice = FakeOnDeviceSummarizer()
    private lateinit var viewModel: QueryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = QueryViewModel(generateAnswer, proposeNotes, saveNote, summarizeNotes, onDevice)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Leaves the ViewModel with a finished answer, so "Save as note" has something to work on. */
    private fun answerAQuestion() {
        viewModel.onQueryChanged("my name is Julia")
        viewModel.onSubmit()
        generateAnswer.answer.complete(RagAnswer(text = "Nice to meet you", citations = emptyList()))
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun summaryOf(engine: SummaryEngine, reason: FallbackReason? = null) =
        NotesSummary(bullets = listOf("a", "b", "c"), engine = engine, fallbackReason = reason)

    private fun summarize(topic: String = "clean architecture") {
        viewModel.onModeSelected(QueryMode.SUMMARIZE)
        viewModel.onQueryChanged(topic)
        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    // ---- Ask ----

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
        assertThat(state.errorMessage).isEqualTo(UiMessage(R.string.query_error_generic))
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

    // ---- Saving notes ----

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
        assertThat(state.noteSaveMessage).isEqualTo(UiMessage(R.string.query_saved_one, listOf("Your name is Julia.")))
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
        assertThat(viewModel.uiState.value.noteSaveMessage).isEqualTo(UiMessage(R.string.query_saved_many, listOf(2)))
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
        assertThat(state.noteSaveMessage).isEqualTo(UiMessage(R.string.query_nothing_to_save))
    }

    @Test
    fun `a failed proposal surfaces an error message`() = runTest(testDispatcher) {
        proposeNotes.shouldFail = true
        answerAQuestion()

        viewModel.saveAsNote()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.noteSaveMessage?.resId).isEqualTo(R.string.query_failed_to_save)
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

        assertThat(viewModel.uiState.value.noteSaveMessage?.resId).isEqualTo(R.string.query_failed_to_save)
        assertThat(viewModel.uiState.value.isSavingNote).isFalse()
    }

    // ---- Summarize ----

    @Test
    fun `Ask is the default mode`() {
        assertThat(viewModel.uiState.value.mode).isEqualTo(QueryMode.ASK)
    }

    @Test
    fun `submitting in Summarize mode summarizes the topic instead of asking`() = runTest(testDispatcher) {
        summarizeNotes.result = summaryOf(SummaryEngine.ON_DEVICE)

        summarize("clean architecture")

        val state = viewModel.uiState.value
        assertThat(summarizeNotes.lastTopic).isEqualTo("clean architecture")
        assertThat(generateAnswer.lastQuery).isNull()
        assertThat(state.summary?.engine).isEqualTo(SummaryEngine.ON_DEVICE)
        assertThat(state.isSummarizing).isFalse()
        assertThat(state.summaryNothingFound).isFalse()
    }

    @Test
    fun `a cloud summary keeps the reason it fell back`() = runTest(testDispatcher) {
        summarizeNotes.result = summaryOf(SummaryEngine.CLOUD, FallbackReason.UNSUPPORTED_DEVICE)

        summarize()

        val summary = viewModel.uiState.value.summary
        assertThat(summary?.engine).isEqualTo(SummaryEngine.CLOUD)
        assertThat(summary?.fallbackReason).isEqualTo(FallbackReason.UNSUPPORTED_DEVICE)
    }

    @Test
    fun `no notes for the topic is reported without an error`() = runTest(testDispatcher) {
        summarizeNotes.result = null

        summarize()

        val state = viewModel.uiState.value
        assertThat(state.summary).isNull()
        assertThat(state.summaryNothingFound).isTrue()
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun `a failed summary surfaces an error and stops the spinner`() = runTest(testDispatcher) {
        summarizeNotes.shouldFail = true

        summarize()

        val state = viewModel.uiState.value
        assertThat(state.errorMessage).isEqualTo(UiMessage(R.string.query_error_generic))
        assertThat(state.isSummarizing).isFalse()
        assertThat(state.summary).isNull()
    }

    @Test
    fun `a blank topic is a no-op in Summarize mode`() = runTest(testDispatcher) {
        summarize("   ")

        assertThat(summarizeNotes.lastTopic).isNull()
        assertThat(viewModel.uiState.value.isSummarizing).isFalse()
    }

    @Test
    fun `switching modes keeps the other mode's result`() = runTest(testDispatcher) {
        answerAQuestion()
        summarizeNotes.result = summaryOf(SummaryEngine.ON_DEVICE)

        summarize()
        viewModel.onModeSelected(QueryMode.ASK)

        val state = viewModel.uiState.value
        assertThat(state.answer?.text).isEqualTo("Nice to meet you")
        assertThat(state.summary).isNotNull()
    }

    @Test
    fun `switching modes clears a stale error`() = runTest(testDispatcher) {
        summarizeNotes.shouldFail = true
        summarize()
        assertThat(viewModel.uiState.value.errorMessage).isNotNull()

        viewModel.onModeSelected(QueryMode.ASK)

        assertThat(viewModel.uiState.value.errorMessage).isNull()
    }

    // ---- On-device model status ----

    @Test
    fun `the model status is not checked until Summarize is opened`() = runTest(testDispatcher) {
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(onDevice.availabilityCalls).isEqualTo(0)
        assertThat(viewModel.uiState.value.modelStatus).isEqualTo(ModelStatus.Unknown)
    }

    @Test
    fun `opening Summarize checks the model once`() = runTest(testDispatcher) {
        onDevice.availability = OnDeviceAvailability.Available

        viewModel.onModeSelected(QueryMode.SUMMARIZE)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onModeSelected(QueryMode.ASK)
        viewModel.onModeSelected(QueryMode.SUMMARIZE)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.modelStatus).isEqualTo(ModelStatus.Ready)
        assertThat(onDevice.availabilityCalls).isEqualTo(1)
    }

    @Test
    fun `a model that can be downloaded is reported as downloadable`() = runTest(testDispatcher) {
        onDevice.availability = OnDeviceAvailability.Downloadable

        viewModel.onModeSelected(QueryMode.SUMMARIZE)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.modelStatus).isEqualTo(ModelStatus.Downloadable)
    }

    @Test
    fun `an unavailable model carries its reason`() = runTest(testDispatcher) {
        onDevice.availability = OnDeviceAvailability.Unavailable(FallbackReason.UNSUPPORTED_DEVICE)

        viewModel.onModeSelected(QueryMode.SUMMARIZE)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.modelStatus)
            .isEqualTo(ModelStatus.Unavailable(FallbackReason.UNSUPPORTED_DEVICE))
    }

    @Test
    fun `a failing status check becomes unavailable with the exception's reason`() = runTest(testDispatcher) {
        onDevice.availabilityError = OnDeviceSummarizationException(FallbackReason.SERVICE_UNAVAILABLE)

        viewModel.onModeSelected(QueryMode.SUMMARIZE)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.modelStatus)
            .isEqualTo(ModelStatus.Unavailable(FallbackReason.SERVICE_UNAVAILABLE))
    }

    @Test
    fun `downloading the model ends with the model ready`() = runTest(testDispatcher) {
        onDevice.availability = OnDeviceAvailability.Downloadable
        viewModel.onModeSelected(QueryMode.SUMMARIZE)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDownloadModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(onDevice.downloadCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value.modelStatus).isEqualTo(ModelStatus.Ready)
    }

    @Test
    fun `a failed download ends unavailable with the failure's reason`() = runTest(testDispatcher) {
        onDevice.availability = OnDeviceAvailability.Downloadable
        onDevice.downloadFails = true
        viewModel.onModeSelected(QueryMode.SUMMARIZE)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDownloadModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.modelStatus)
            .isEqualTo(ModelStatus.Unavailable(FallbackReason.NOT_ENOUGH_DISK_SPACE))
    }

    @Test
    fun `download is ignored unless the model is downloadable`() = runTest(testDispatcher) {
        onDevice.availability = OnDeviceAvailability.Available
        viewModel.onModeSelected(QueryMode.SUMMARIZE)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDownloadModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(onDevice.downloadCalls).isEqualTo(0)
    }
}
