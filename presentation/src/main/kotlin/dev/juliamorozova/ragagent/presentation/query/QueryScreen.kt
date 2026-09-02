package dev.juliamorozova.ragagent.presentation.query

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                label = { Text("Ask about the knowledge base") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = viewModel::onSubmit) {
                Text("Ask")
            }

            when {
                state.isLoading -> CircularProgressIndicator()
                state.errorMessage != null -> Text(
                    text = state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                )
                state.answer != null -> Text(text = state.answer?.text.orEmpty())
            }
        }
    }
}
