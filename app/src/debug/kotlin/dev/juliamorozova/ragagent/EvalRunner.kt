package dev.juliamorozova.ragagent

import android.content.Context
import android.os.Build
import dev.juliamorozova.ragagent.domain.model.Chunk
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
import dev.juliamorozova.ragagent.domain.summary.OnDeviceAvailability
import dev.juliamorozova.ragagent.domain.summary.OnDeviceSummarizationException
import dev.juliamorozova.ragagent.domain.summary.OnDeviceSummarizer
import dev.juliamorozova.ragagent.domain.summary.RoutingMode
import dev.juliamorozova.ragagent.domain.usecase.SummarizeNotesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/**
 * Bumped whenever how chunks are chosen changes, so cached chunks and results of an older rule are never
 * mixed with new ones (the files of the previous version stay untouched).
 * v1: the 7 best chunks of any notes. v2: the best chunks of the single best-matching note, at most 5.
 */
private const val EVAL_VERSION = "v2"

/** How often and how many times to retry a busy on-device model before giving up on a run. */
private const val BUSY_RETRY_MS = 3_000L
private const val MAX_BUSY_RETRIES = 8

/** Personal chat notes would change what retrieval returns, so they stay out of the evaluation. */
private const val CHAT_NOTE_PREFIX = "chat-note-"

/**
 * Debug-only evaluation of the two summarizers on the same chunks.
 *
 * Retrieval is the slow part (each topic is embedded through the rate-limited Voyage client), so
 * chunks are fetched once per topic and cached in a file; both engines then summarize exactly those
 * chunks. Results accumulate in a second file and are exported as one JSON document for scoring.
 */
class EvalRunner(
    private val context: Context,
    private val summarizeNotes: SummarizeNotesUseCase,
    private val onDevice: OnDeviceSummarizer,
    private val log: (String) -> Unit,
) {

    /**
     * @param repeats Runs per topic. Claude's output varies between runs, so it gets three; the first
     * evaluation showed the on-device model returning word-for-word identical text each time, so one is enough.
     */
    enum class Engine(val key: String, val mode: RoutingMode, val repeats: Int) {
        CLAUDE("claude", RoutingMode.CLOUD_ONLY, repeats = 3),
        NANO("nano", RoutingMode.ON_DEVICE_ONLY, repeats = 1),
    }

    private val directory: File get() = requireNotNull(context.getExternalFilesDir(null))
    private val chunksFile: File get() = File(directory, "eval-chunks-$EVAL_VERSION.json")
    private val resultsFile: File get() = File(directory, "eval-results-$EVAL_VERSION.json")

    val outputPath: String get() = directory.absolutePath

    private fun topics(): JSONArray =
        JSONArray(context.assets.open("eval/topics.json").bufferedReader().use { it.readText() })

    private fun readJson(file: File): JSONObject = if (file.exists()) JSONObject(file.readText()) else JSONObject()

    /** Fetches the chunks for every topic that has none cached yet. */
    suspend fun prepareChunks() {
        val topics = topics()
        val stored = readJson(chunksFile)
        for (index in 0 until topics.length()) {
            val topic = topics.getJSONObject(index)
            val id = topic.getString("id")
            if (stored.has(id)) {
                log("cached ${index + 1}/${topics.length()} $id")
                continue
            }
            val chunks = summarizeNotes.selectChunks(topic.getString("topic"))
                .filterNot { it.chunk.documentId.startsWith(CHAT_NOTE_PREFIX) }
            stored.put(id, JSONArray().apply { chunks.forEach { put(it.toJson()) } })
            chunksFile.writeText(stored.toString(2))
            log("prepared ${index + 1}/${topics.length()} $id: ${chunks.size} chunks")
        }
    }

    suspend fun run(engine: Engine) {
        val stored = readJson(chunksFile)
        if (stored.length() == 0) {
            log("No chunks yet: run \"1. Prepare chunks\" first.")
            return
        }
        if (engine == Engine.NANO && !nanoIsAvailable()) return

        val topics = topics()
        val results = readJson(resultsFile)

        if (engine == Engine.NANO) {
            // The first on-device call pays for loading the model; keep it out of the timings.
            val first = topics.getJSONObject(0)
            runCatching {
                summarizeNotes.summarize(chunksOf(stored, first.getString("id")), engine.mode, first.getString("topic"))
            }
            log("warm-up done")
        }

        for (index in 0 until topics.length()) {
            val topic = topics.getJSONObject(index)
            val id = topic.getString("id")
            val chunks = chunksOf(stored, id)
            val runs = JSONArray()
            repeat(engine.repeats) { runs.put(runPatiently(chunks, engine, it + 1, topic.getString("topic"))) }

            val topicResults = results.optJSONObject(id) ?: JSONObject().also { results.put(id, it) }
            topicResults.put(engine.key, runs)
            resultsFile.writeText(results.toString(2))
            log("${engine.key} ${index + 1}/${topics.length()} $id: ${summaryLine(runs)}")
        }
        log("${engine.key} done. Results are saved; tap \"Copy results\".")
    }

    private suspend fun nanoIsAvailable(): Boolean {
        val availability = try {
            onDevice.availability()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Nano availability check failed: ${e.describe()}")
            return false
        }
        if (availability != OnDeviceAvailability.Available) {
            log("Nano is not available ($availability): nothing to run on this device.")
            return false
        }
        return true
    }

    /**
     * The on-device model reports BUSY for a while after a finished call (the router's own short
     * retries are not enough for back-to-back runs). Wait and retry here so a busy model does not count
     * as a quality failure, and record how long it took: that is the real BUSY window.
     */
    private suspend fun runPatiently(
        chunks: List<RetrievedChunk>,
        engine: Engine,
        run: Int,
        topic: String,
    ): JSONObject {
        var record = runOnce(chunks, engine, run, topic)
        var retries = 0
        while (engine == Engine.NANO && record.optString("reason") == "BUSY" && retries < MAX_BUSY_RETRIES) {
            delay(BUSY_RETRY_MS)
            retries++
            record = runOnce(chunks, engine, run, topic)
        }
        if (retries > 0) record.put("busyRetries", retries).put("busyWaitedMs", retries * BUSY_RETRY_MS)
        return record
    }

    private suspend fun runOnce(chunks: List<RetrievedChunk>, engine: Engine, run: Int, topic: String): JSONObject {
        val record = JSONObject().put("run", run)
        return try {
            val summary = summarizeNotes.summarize(chunks, engine.mode, topic)
                ?: return record.put("error", "no chunks for this topic")
            record
                .put("bullets", JSONArray(summary.bullets))
                .put("durationMs", summary.durationMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            record.put("error", e.describe())
            (e as? OnDeviceSummarizationException)?.let { record.put("reason", it.reason.name) }
            record
        }
    }

    /** Everything needed to score the run: topics, key points, the exact chunks, and every run's output. */
    fun exportJson(): String {
        val chunks = readJson(chunksFile)
        val results = readJson(resultsFile)
        val topics = topics()
        val exported = JSONArray()
        for (index in 0 until topics.length()) {
            val topic = topics.getJSONObject(index)
            val id = topic.getString("id")
            exported.put(
                JSONObject()
                    .put("id", id)
                    .put("topic", topic.getString("topic"))
                    .put("keyPoints", topic.getJSONArray("keyPoints"))
                    .put("note", topic.optString("note", ""))
                    .put("chunks", chunks.optJSONArray(id) ?: JSONArray())
                    .put("runs", results.optJSONObject(id) ?: JSONObject()),
            )
        }
        val meta = JSONObject()
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("api", Build.VERSION.SDK_INT)
            .put("evalVersion", EVAL_VERSION)
            .put("repeatsPerEngine", JSONObject().apply { Engine.entries.forEach { put(it.key, it.repeats) } })
            .put("exportedAt", Instant.now().toString())
        return JSONObject().put("meta", meta).put("topics", exported).toString(2)
    }

    /** Forgets the cached chunks and results, e.g. after changing the knowledge base. */
    fun reset() {
        chunksFile.delete()
        resultsFile.delete()
    }

    private fun chunksOf(stored: JSONObject, topicId: String): List<RetrievedChunk> {
        val array = stored.optJSONArray(topicId) ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it).toRetrievedChunk() }
    }

    private fun summaryLine(runs: JSONArray): String {
        val durations = (0 until runs.length())
            .map { runs.getJSONObject(it) }
            .filter { !it.has("error") }
            .map { it.getLong("durationMs") }
            .sorted()
        val median = if (durations.isEmpty()) "-" else "${durations[durations.size / 2]} ms"
        return "${durations.size}/${runs.length()} ok, median $median"
    }

    private fun RetrievedChunk.toJson() = JSONObject()
        .put("id", chunk.id)
        .put("documentId", chunk.documentId)
        .put("position", chunk.position)
        .put("score", score.toDouble())
        .put("createdAtMillis", chunk.createdAtMillis)
        .put("text", chunk.text)

    private fun JSONObject.toRetrievedChunk() = RetrievedChunk(
        chunk = Chunk(
            id = getString("id"),
            documentId = getString("documentId"),
            text = getString("text"),
            position = getInt("position"),
            createdAtMillis = getLong("createdAtMillis"),
        ),
        score = getDouble("score").toFloat(),
    )

    private fun Throwable.describe() = "${this::class.simpleName}: $message"
}
