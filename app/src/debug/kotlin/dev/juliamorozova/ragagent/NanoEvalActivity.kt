package dev.juliamorozova.ragagent

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import dev.juliamorozova.ragagent.domain.summary.OnDeviceSummarizer
import dev.juliamorozova.ragagent.domain.usecase.SummarizeNotesUseCase
import dev.juliamorozova.ragagent.presentation.theme.RagAgentTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "NanoEval"

/**
 * Debug-only screen that compares the on-device and the cloud summarizer on the same chunks
 * (see [EvalRunner]). Opened from the app icon's long-press shortcuts in debug builds.
 */
@AndroidEntryPoint
class NanoEvalActivity : ComponentActivity() {

    @Inject lateinit var summarizeNotes: SummarizeNotesUseCase
    @Inject lateinit var onDevice: OnDeviceSummarizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RagAgentTheme {
                Surface {
                    NanoEvalScreen(summarizeNotes, onDevice)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NanoEvalScreen(summarizeNotes: SummarizeNotesUseCase, onDevice: OnDeviceSummarizer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val log = remember { mutableStateListOf<String>() }
    var running by remember { mutableStateOf(false) }

    val runner = remember {
        EvalRunner(context, summarizeNotes, onDevice) { line ->
            Log.d(TAG, line)
            log += line
        }
    }

    LaunchedEffect(Unit) {
        log += "Device: ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}"
        log += "Files: ${runner.outputPath}"
    }

    fun launchTask(label: String, block: suspend () -> Unit) {
        if (running) return
        running = true
        scope.launch {
            log += "--- $label"
            try {
                block()
            } catch (e: Throwable) {
                log += "ERROR ${e::class.simpleName}: ${e.message}"
            } finally {
                running = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Eval: Nano vs Claude", style = MaterialTheme.typography.titleMedium)
        Text(
            "Use a fresh install (clear app data) so chat notes do not affect retrieval. " +
                "Step 1 takes about 5 minutes: each topic is embedded through the rate-limited Voyage client.",
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !running, onClick = { launchTask("prepare chunks") { runner.prepareChunks() } }) {
                Text("1. Prepare chunks")
            }
            Button(enabled = !running, onClick = { launchTask("run Claude x3") { runner.run(EvalRunner.Engine.CLAUDE) } }) {
                Text("2. Run Claude")
            }
            Button(enabled = !running, onClick = { launchTask("run Nano x3") { runner.run(EvalRunner.Engine.NANO) } }) {
                Text("3. Run Nano")
            }
            Button(onClick = { clipboard.setText(AnnotatedString(runner.exportJson())) }) { Text("Copy results") }
            Button(enabled = !running, onClick = { runner.reset(); log += "cleared cached chunks and results" }) {
                Text("Reset")
            }
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
