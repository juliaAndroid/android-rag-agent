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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CITATION_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d")

private fun citationLabel(documentId: String, createdAtMillis: Long): String {
    if (documentId.startsWith("chat-note-")) {
        val date = Instant.ofEpochMilli(createdAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return "Your note · ${date.format(CITATION_DATE_FORMATTER)}"
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

/** Query/response screen: text input, submit, answer with source citations. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QueryScreen(
    modifier: Modifier = Modifier,
    viewModel: QueryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.noteSaveMessage) {
        val message = state.noteSaveMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        viewModel.onNoteSaveMessageShown()
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
                    state.isLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.answerNarration ?: "Thinking…",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.errorMessage != null -> Text(
                        text = state.errorMessage.orEmpty(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error,
                    )
                    state.answer != null -> Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Top,
                    ) {
                        Text(
                            text = state.answer?.text.orEmpty(),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        val citations = state.answer?.citations.orEmpty()
                        if (citations.isNotEmpty()) {
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
                                                Text(
                                                    citationLabel(
                                                        citation.chunk.documentId,
                                                        citation.chunk.createdAtMillis,
                                                    ),
                                                )
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
                                TextButton(onClick = viewModel::saveAsNote) {
                                    Text("Save as note")
                                }
                            }
                        }
                    }
                    else -> Text(
                        text = "Ask something about Clean Architecture, KMM, or mobile AI security",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            ) {
                TextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChanged,
                    placeholder = { Text("Ask about the knowledge base") },
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
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Ask")
            }
        }
    }
}
