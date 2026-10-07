package dev.juliamorozova.ragagent

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
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
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import dev.juliamorozova.ragagent.rag.chunking.SlidingWindowChunker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// SPIKE (branch `nano`): throwaway screen to learn how ML Kit GenAI Summarization behaves on a
// real device. Not meant to be merged; the real integration will live behind a domain interface.

private const val TAG = "NanoSpike"
private const val KB_DIR = "knowledge-base"
private const val CLEAN_ARCH_FILE = "clean-architecture-boundaries.md"
private const val KMM_FILE = "kmm-shared-logic.md"
private const val MASVS_FILE = "owasp-masvs-ai-pr-review.md"

private val SHORT_TEXT = "Clean Architecture splits an app into domain, data and presentation. " +
    "The domain layer has no Android dependency."

private data class Config(val input: Int, val output: Int, val truncate: Boolean = false)

private val ARTICLE_3 = Config(SummarizerOptions.InputType.ARTICLE, SummarizerOptions.OutputType.THREE_BULLETS)

private fun Int.statusName() = when (this) {
    FeatureStatus.UNAVAILABLE -> "UNAVAILABLE"
    FeatureStatus.DOWNLOADABLE -> "DOWNLOADABLE"
    FeatureStatus.DOWNLOADING -> "DOWNLOADING"
    FeatureStatus.AVAILABLE -> "AVAILABLE"
    else -> "UNKNOWN($this)"
}

private fun Int.errorName() = when (this) {
    GenAiException.ErrorCode.UNKNOWN -> "UNKNOWN"
    GenAiException.ErrorCode.REQUEST_PROCESSING_ERROR -> "REQUEST_PROCESSING_ERROR"
    GenAiException.ErrorCode.CANCELLED -> "CANCELLED"
    GenAiException.ErrorCode.NOT_AVAILABLE -> "NOT_AVAILABLE"
    GenAiException.ErrorCode.BUSY -> "BUSY"
    GenAiException.ErrorCode.RESPONSE_PROCESSING_ERROR -> "RESPONSE_PROCESSING_ERROR"
    GenAiException.ErrorCode.REQUEST_TOO_LARGE -> "REQUEST_TOO_LARGE"
    GenAiException.ErrorCode.REQUEST_TOO_SMALL -> "REQUEST_TOO_SMALL"
    GenAiException.ErrorCode.RESPONSE_GENERATION_ERROR -> "RESPONSE_GENERATION_ERROR"
    GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> "NOT_ENOUGH_DISK_SPACE"
    GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE -> "NEEDS_SYSTEM_UPDATE"
    GenAiException.ErrorCode.AICORE_INCOMPATIBLE -> "AICORE_INCOMPATIBLE"
    else -> "CODE_$this"
}

private fun Config.label() = buildString {
    append(if (input == SummarizerOptions.InputType.ARTICLE) "ARTICLE" else "CONVERSATION")
    append("/")
    append(output)
    append("bullet")
    if (truncate) append("/autoTruncate")
}

private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (e: ExecutionException) {
                continuation.resumeWithException(e.cause ?: e)
            } catch (e: Throwable) {
                continuation.resumeWithException(e)
            }
        },
        Runnable::run,
    )
    continuation.invokeOnCancellation { cancel(false) }
}

private fun Throwable.describe(): String {
    val code = (this as? GenAiException)?.errorCode?.let { " errorCode=${it.errorName()}($it)" }.orEmpty()
    return "${this::class.simpleName}$code: $message"
}

private fun Context.newSummarizer(config: Config): Summarizer = Summarization.getClient(
    SummarizerOptions.builder(this)
        .setInputType(config.input)
        .setOutputType(config.output)
        .setLanguage(SummarizerOptions.Language.ENGLISH)
        .setLongInputAutoTruncationEnabled(config.truncate)
        .build(),
)

private fun Context.readKb(name: String): String =
    assets.open("$KB_DIR/$name").bufferedReader().use { it.readText() }

/** Five 800-char chunks, the size and count the real "summarize my notes" path would feed in. */
private fun Context.fiveChunks(): String {
    val chunker = SlidingWindowChunker()
    val chunks = chunker(CLEAN_ARCH_FILE, readKb(CLEAN_ARCH_FILE)).take(2) +
        chunker(KMM_FILE, readKb(KMM_FILE)).take(3)
    return chunks.joinToString("\n\n") { it.text }
}

/** One inference with a fresh summarizer, reported on a single line plus the summary text. */
private suspend fun runOnce(
    context: Context,
    label: String,
    text: String,
    config: Config,
    out: (String) -> Unit,
    streaming: Boolean = true,
) {
    val summarizer = context.newSummarizer(config)
    val mode = if (streaming) "stream" else "plain"
    try {
        val start = SystemClock.elapsedRealtime()
        var firstChunkMs = -1L
        val request = SummarizationRequest.builder(text).build()
        val result = if (streaming) {
            summarizer.runInference(request) { _ ->
                if (firstChunkMs < 0) firstChunkMs = SystemClock.elapsedRealtime() - start
            }.await()
        } else {
            summarizer.runInference(request).await()
        }
        val total = SystemClock.elapsedRealtime() - start
        out("[$label | ${config.label()} | $mode] in=${text.length}ch first=${firstChunkMs}ms total=${total}ms")
        out(result.summary)
    } catch (e: Throwable) {
        out("[$label | ${config.label()} | $mode] in=${text.length}ch ERROR ${e.describe()}")
    } finally {
        summarizer.close()
    }
}

private const val PLAIN_PROSE = "The Moon is Earth's only natural satellite. It orbits our planet at an average " +
    "distance of about 384,000 kilometres and takes roughly 27 days to complete one orbit. Because the Moon " +
    "rotates at the same rate as it orbits, the same side always faces the Earth. The Moon has no breathable " +
    "atmosphere, and its surface is covered with craters formed by ancient impacts. Its gravity is about one " +
    "sixth of Earth's, which is why astronauts could bounce across the surface during the Apollo missions. " +
    "Tides in the oceans are caused mainly by the Moon's gravitational pull on the water."

private const val DIALOGUE = "User: I'm planning the architecture of my Android app. Should I split it into modules?\n" +
    "Assistant: Yes, a multi-module setup keeps boundaries clear and speeds up builds as the app grows.\n" +
    "User: Which modules would you start with?\n" +
    "Assistant: Start with domain for business rules, data for storage and network, presentation for the UI, " +
    "and an app module that wires them together.\n" +
    "User: Does the domain module need Android dependencies?\n" +
    "Assistant: No, keeping it plain Kotlin makes it easy to test on the JVM and reuse elsewhere."

/** Drops the most common markdown markers so a text reads like plain prose. */
private fun String.stripMarkdown(): String = lineSequence()
    .map { it.trim().trimStart('#', '-', '*', '>', '|', ' ').replace("`", "").replace("**", "") }
    .filter { it.isNotBlank() }
    .joinToString("\n")

/** Narrows down why every real summarization came back with the policy-check error. */
private suspend fun runDiagnose(context: Context, out: (String) -> Unit) {
    out("=== NANO DIAGNOSE ===")
    out("Device: ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}")
    val kbRaw = context.readKb(CLEAN_ARCH_FILE)
    val kbPlain = kbRaw.stripMarkdown()
    val chunksPlain = context.fiveChunks().stripMarkdown()

    out("--- plain English prose (no markdown), streaming vs plain call")
    runOnce(context, "prose", PLAIN_PROSE, ARTICLE_3, out, streaming = true)
    runOnce(context, "prose", PLAIN_PROSE, ARTICLE_3, out, streaming = false)

    out("--- knowledge-base file: raw markdown vs stripped, plain call")
    runOnce(context, "kb raw", kbRaw, ARTICLE_3, out, streaming = false)
    runOnce(context, "kb stripped", kbPlain, ARTICLE_3, out, streaming = false)

    out("--- five chunks, stripped, plain call")
    runOnce(context, "5 chunks stripped", chunksPlain, ARTICLE_3, out, streaming = false)

    out("--- real dialogue as CONVERSATION")
    runOnce(
        context, "dialogue", DIALOGUE,
        Config(SummarizerOptions.InputType.CONVERSATION, SummarizerOptions.OutputType.THREE_BULLETS),
        out, streaming = false,
    )
    out("=== END ===")
}

private suspend fun runAll(context: Context, out: (String) -> Unit) {
    out("=== NANO SPIKE REPORT ===")
    out("Device: ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}")

    val probe = context.newSummarizer(ARTICLE_3)
    try {
        var status = try {
            probe.checkFeatureStatus().await()
        } catch (e: Throwable) {
            out("status ERROR ${e.describe()}")
            return
        }
        out("status: ${status.statusName()}")
        try {
            out("base model: ${probe.getBaseModelName().await()}")
        } catch (e: Throwable) {
            out("base model ERROR ${e.describe()}")
        }

        if (status == FeatureStatus.DOWNLOADABLE) {
            val t0 = SystemClock.elapsedRealtime()
            var totalBytes = -1L
            var lastReportedStep = -1L
            try {
                probe.downloadFeature(
                    object : DownloadCallback {
                        override fun onDownloadStarted(bytesToDownload: Long) {
                            totalBytes = bytesToDownload
                            out("download started: $bytesToDownload bytes")
                        }
                        override fun onDownloadProgress(totalBytesDownloaded: Long) {
                            // Report roughly every 5% so the screen shows it is alive without flooding.
                            val step = if (totalBytes > 0) totalBytesDownloaded * 20 / totalBytes else -1L
                            if (step > lastReportedStep) {
                                lastReportedStep = step
                                out("downloaded ${totalBytesDownloaded / 1_000_000} MB (${step * 5}%)")
                            }
                        }
                        override fun onDownloadCompleted() = Unit
                        override fun onDownloadFailed(e: GenAiException) = out("download FAILED ${e.describe()}")
                    },
                ).await()
                out("download: $totalBytes bytes in ${SystemClock.elapsedRealtime() - t0} ms")
            } catch (e: Throwable) {
                out("download ERROR ${e.describe()}")
            }
            status = runCatching { probe.checkFeatureStatus().await() }.getOrDefault(status)
            out("status after download: ${status.statusName()}")
        }

        // A download started earlier (or still finishing) keeps running inside AICore: wait for it
        // here instead of giving up, so one tap is enough even in a short testing window.
        if (status == FeatureStatus.DOWNLOADING) {
            val waitStart = SystemClock.elapsedRealtime()
            out("model is downloading, waiting up to 20 min...")
            while (status == FeatureStatus.DOWNLOADING && SystemClock.elapsedRealtime() - waitStart < 20 * 60_000L) {
                delay(5_000)
                status = runCatching { probe.checkFeatureStatus().await() }.getOrDefault(status)
            }
            out("waited ${(SystemClock.elapsedRealtime() - waitStart) / 1000} s, status: ${status.statusName()}")
        }
        if (status != FeatureStatus.AVAILABLE) {
            out("Model not available, running one inference anyway to record the error.")
            runOnce(context, "probe", context.readKb(CLEAN_ARCH_FILE), ARTICLE_3, out)
            out("=== END ===")
            return
        }

        val t1 = SystemClock.elapsedRealtime()
        try {
            probe.prepareInferenceEngine().await()
            out("warm up: ${SystemClock.elapsedRealtime() - t1} ms")
        } catch (e: Throwable) {
            out("warm up ERROR ${e.describe()}")
        }
    } finally {
        probe.close()
    }

    val kb = context.readKb(CLEAN_ARCH_FILE)
    val chunks = context.fiveChunks()
    val veryLong = (1..6).joinToString("\n\n") { context.readKb(MASVS_FILE) }

    out("--- output size: 1, 2, 3 bullets (ARTICLE, KB file)")
    listOf(
        SummarizerOptions.OutputType.ONE_BULLET,
        SummarizerOptions.OutputType.TWO_BULLETS,
        SummarizerOptions.OutputType.THREE_BULLETS,
    ).forEach { runOnce(context, "kb", kb, Config(SummarizerOptions.InputType.ARTICLE, it), out) }

    out("--- input type: CONVERSATION vs ARTICLE (3 bullets, KB file)")
    runOnce(context, "kb", kb, Config(SummarizerOptions.InputType.CONVERSATION, SummarizerOptions.OutputType.THREE_BULLETS), out)

    out("--- realistic input: five 800-char chunks")
    runOnce(context, "5 chunks", chunks, ARTICLE_3, out)

    out("--- size limits")
    runOnce(context, "short", SHORT_TEXT, ARTICLE_3, out)
    runOnce(context, "very long", veryLong, ARTICLE_3, out)
    runOnce(context, "very long", veryLong, ARTICLE_3.copy(truncate = true), out)

    out("--- latency on a warm engine (KB file, 3 bullets, 3 runs)")
    repeat(3) {
        runOnce(context, "repeat ${it + 1}", kb, ARTICLE_3, out = { line -> if (line.startsWith("[")) out(line) })
    }

    out("=== END ===")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NanoSpikeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val log = remember { mutableStateListOf<String>() }
    var report by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }

    fun say(message: String) {
        Log.d(TAG, message)
        log += message
    }

    LaunchedEffect(Unit) {
        say("Device: ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}")
    }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Nano spike (ML Kit GenAI Summarization)", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !running,
                onClick = {
                    running = true
                    log.clear()
                    scope.launch {
                        val lines = mutableListOf<String>()
                        try {
                            runAll(context) { line ->
                                say(line)
                                lines += line
                            }
                        } catch (e: Throwable) {
                            say("RUN ALL ERROR ${e.describe()}")
                            lines += "RUN ALL ERROR ${e.describe()}"
                        } finally {
                            report = lines.joinToString("\n")
                            running = false
                        }
                    }
                },
            ) { Text(if (running) "Running…" else "Run all") }
            Button(
                enabled = !running,
                onClick = {
                    running = true
                    log.clear()
                    scope.launch {
                        val lines = mutableListOf<String>()
                        try {
                            runDiagnose(context) { line ->
                                say(line)
                                lines += line
                            }
                        } catch (e: Throwable) {
                            lines += "DIAGNOSE ERROR ${e.describe()}"
                            say("DIAGNOSE ERROR ${e.describe()}")
                        } finally {
                            report = lines.joinToString("\n")
                            running = false
                        }
                    }
                },
            ) { Text("Diagnose") }
            Button(
                enabled = report.isNotEmpty(),
                onClick = { clipboard.setText(AnnotatedString(report)) },
            ) { Text("Copy report") }
            Button(onClick = { log.clear() }) { Text("Clear") }
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
