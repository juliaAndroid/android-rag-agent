package dev.juliamorozova.ragagent.presentation.query

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.juliamorozova.ragagent.domain.model.FallbackReason
import dev.juliamorozova.ragagent.domain.model.NotesSummary
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
import dev.juliamorozova.ragagent.domain.model.SummaryEngine
import dev.juliamorozova.ragagent.presentation.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CITATION_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d")

@Composable
private fun citationLabel(documentId: String, createdAtMillis: Long): String {
    if (documentId.startsWith("chat-note-")) {
        val date = Instant.ofEpochMilli(createdAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return stringResource(R.string.query_citation_note, date.format(CITATION_DATE_FORMATTER))
    }
    return documentId.removeSuffix(".md")
}

private const val MIN_LENGTH_TO_TRIM = 40
private const val BOUNDARY_SEARCH_MARGIN = 20

/**
 * Display-only cleanup for chunk text sliced at fixed character offsets (see SlidingWindowChunker),
 * which can cut mid-word at either edge. Only trims when a whitespace boundary is close enough to
 * the edge to be confident it's a chunk seam rather than just short text.
 */
private fun String.trimToWordBoundaries(): String {
    if (length < MIN_LENGTH_TO_TRIM) return this

    val firstWhitespaceIndex = indexOfFirst { it.isWhitespace() }
    val startIndex = if (firstWhitespaceIndex in 0 until BOUNDARY_SEARCH_MARGIN) {
        firstWhitespaceIndex + 1
    } else {
        0
    }

    val lastWhitespaceIndex = indexOfLast { it.isWhitespace() }
    val endIndex = if (lastWhitespaceIndex >= 0 && length - lastWhitespaceIndex <= BOUNDARY_SEARCH_MARGIN) {
        lastWhitespaceIndex
    } else {
        length
    }

    if (startIndex >= endIndex) return this

    val trimmedStart = startIndex > 0
    val trimmedEnd = endIndex < length
    val core = substring(startIndex, endIndex)
    return buildString {
        if (trimmedStart) append('…')
        append(core)
        if (trimmedEnd) append('…')
    }
}

/** Query/response screen: Ask a question or Summarize a topic, with source citations. */
@Composable
fun QueryScreen(
    modifier: Modifier = Modifier,
    viewModel: QueryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isBusy = state.isLoading || state.isSummarizing

    // Resolved while composing (string resources need it), then shown once per message.
    val noteSaveText = state.noteSaveMessage?.asString()
    LaunchedEffect(state.noteSaveMessage) {
        val message = noteSaveText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        viewModel.onNoteSaveMessageShown()
    }

    if (state.pendingNotes.isNotEmpty()) {
        SaveNoteDialog(
            notes = state.pendingNotes,
            onConfirm = viewModel::onConfirmSave,
            onDismiss = viewModel::onDismissSave,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().imePadding()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isBusy -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = when (state.mode) {
                                QueryMode.ASK -> state.answerNarration ?: stringResource(R.string.query_thinking)
                                QueryMode.SUMMARIZE -> stringResource(R.string.query_summarizing)
                            },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.errorMessage != null -> Text(
                        text = state.errorMessage?.asString().orEmpty(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error,
                    )
                    state.mode == QueryMode.ASK && state.answer != null -> AnswerContent(
                        state = state,
                        onSaveAsNote = viewModel::saveAsNote,
                    )
                    state.mode == QueryMode.SUMMARIZE && state.summary != null -> SummaryContent(
                        summary = state.summary!!,
                    )
                    state.mode == QueryMode.SUMMARIZE && state.summaryNothingFound -> HintText(R.string.query_nothing_found)
                    state.mode == QueryMode.ASK -> HintText(R.string.query_empty_ask)
                    else -> HintText(R.string.query_empty_summarize)
                }
            }

            if (state.mode == QueryMode.SUMMARIZE) {
                ModelStatusRow(status = state.modelStatus, onDownload = viewModel::onDownloadModel)
            }

            Spacer(modifier = Modifier.height(8.dp))

            ModeSelector(mode = state.mode, onModeSelected = viewModel::onModeSelected)

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            ) {
                TextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChanged,
                    placeholder = {
                        Text(
                            stringResource(
                                when (state.mode) {
                                    QueryMode.ASK -> R.string.query_hint_ask
                                    QueryMode.SUMMARIZE -> R.string.query_hint_summarize
                                },
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = viewModel::onSubmit,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        when (state.mode) {
                            QueryMode.ASK -> R.string.query_action_ask
                            QueryMode.SUMMARIZE -> R.string.query_action_summarize
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun HintText(resId: Int) {
    Text(
        text = stringResource(resId),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ModeSelector(mode: QueryMode, onModeSelected: (QueryMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = mode == QueryMode.ASK,
            onClick = { onModeSelected(QueryMode.ASK) },
            label = { Text(stringResource(R.string.query_mode_ask)) },
        )
        FilterChip(
            selected = mode == QueryMode.SUMMARIZE,
            onClick = { onModeSelected(QueryMode.SUMMARIZE) },
            label = { Text(stringResource(R.string.query_mode_summarize)) },
        )
    }
}

@Composable
private fun SaveNoteDialog(notes: List<String>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    // Shows exactly what will be stored, as plain text, before anything is written.
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (notes.size == 1) R.string.query_save_note_title_one else R.string.query_save_note_title_many,
                ),
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                notes.forEach { note -> Text(stringResource(R.string.query_note_bullet, note)) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.query_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.query_cancel)) } },
    )
}

@Composable
private fun AnswerContent(state: QueryUiState, onSaveAsNote: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = state.answer?.text.orEmpty(),
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )

        SourceChips(state.answer?.citations.orEmpty())

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            if (state.isSavingNote) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                state.noteSaveNarration?.let { narration ->
                    Text(text = narration, modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                TextButton(onClick = onSaveAsNote) {
                    Text(stringResource(R.string.query_save_as_note))
                }
            }
        }
    }
}

@Composable
private fun SummaryContent(summary: NotesSummary) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EngineBadge(summary.engine)
                summary.bullets.forEach { bullet ->
                    Text(stringResource(R.string.query_note_bullet, bullet))
                }
            }
        }
        summary.fallbackReason?.let { reason -> FallbackNote(reason) }
        SourceChips(summary.sources)
    }
}

@Composable
private fun EngineBadge(engine: SummaryEngine) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = stringResource(
                when (engine) {
                    SummaryEngine.ON_DEVICE -> R.string.query_badge_on_device
                    SummaryEngine.CLOUD -> R.string.query_badge_cloud
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun FallbackNote(reason: FallbackReason) {
    Text(
        text = stringResource(reason.toStringRes()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ModelStatusRow(status: ModelStatus, onDownload: () -> Unit) {
    when (status) {
        ModelStatus.Unknown -> Unit
        ModelStatus.Checking -> StatusText(stringResource(R.string.query_model_checking))
        ModelStatus.Ready -> StatusText(stringResource(R.string.query_model_ready))
        ModelStatus.Downloadable -> Row(verticalAlignment = Alignment.CenterVertically) {
            StatusText(stringResource(R.string.query_model_not_downloaded))
            TextButton(onClick = onDownload) { Text(stringResource(R.string.query_model_download)) }
        }
        is ModelStatus.Downloading -> Column {
            StatusText(stringResource(R.string.query_model_downloading, status.percent))
            LinearProgressIndicator(progress = { status.percent / 100f }, modifier = Modifier.fillMaxWidth())
        }
        is ModelStatus.Unavailable -> StatusText(
            stringResource(
                if (status.reason == FallbackReason.SERVICE_UNAVAILABLE) {
                    R.string.query_model_aicore_off
                } else {
                    R.string.query_model_unavailable
                },
            ),
        )
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Source chips; tapping one expands the chunk text it came from. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceChips(citations: List<RetrievedChunk>) {
    if (citations.isEmpty()) return

    Spacer(modifier = Modifier.height(12.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        citations.forEach { citation ->
            var expanded by remember { mutableStateOf(false) }
            Column {
                AssistChip(
                    onClick = { expanded = !expanded },
                    label = {
                        Text(citationLabel(citation.chunk.documentId, citation.chunk.createdAtMillis))
                    },
                )
                if (expanded) {
                    Surface(
                        modifier = Modifier.padding(top = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = citation.chunk.text.trimToWordBoundaries(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}
