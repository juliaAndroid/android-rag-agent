package dev.juliamorozova.ragagent.presentation.query

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Query/response screen: text input, submit, answer. Source-citation display isn't
 * wired up yet — see README status.
 */
@Composable
fun QueryScreen(
    modifier: Modifier = Modifier,
    viewModel: QueryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.noteSaveMessage) {
        val message = state.noteSaveMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onNoteSaveMessageShown()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                label = { Text("Ask about the knowledge base") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = viewModel::onSubmit, enabled = !state.isLoading) {
                Text("Ask")
            }

            when {
                state.isLoading -> CircularProgressIndicator()
                state.errorMessage != null -> Text(
                    text = state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                )
                state.answer != null -> Column {
                    Text(text = state.answer?.text.orEmpty())
                    Row {
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
            }
        }
    }
}
