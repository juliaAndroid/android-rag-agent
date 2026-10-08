package dev.juliamorozova.ragagent.domain.summary

import dev.juliamorozova.ragagent.domain.model.FallbackReason
import dev.juliamorozova.ragagent.domain.model.NotesSummary
import dev.juliamorozova.ragagent.domain.model.RetrievedChunk
import dev.juliamorozova.ragagent.domain.model.SummaryEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Which summarizer a request may use. [AUTO] is the normal app behaviour; the others pin an engine (e.g. for eval). */
enum class RoutingMode { AUTO, ON_DEVICE_ONLY, CLOUD_ONLY }

/**
 * Chooses between the on-device and the cloud summarizer. Deterministic on purpose: every
 * outcome of the on-device model maps to a [FallbackReason] and a fixed decision, so each
 * fallback path is unit-testable with fakes.
 */
class SummaryRouter(
    private val onDevice: OnDeviceSummarizer,
    private val cloud: CloudSummarizer,
    // Measured on a Galaxy S25: after a finished call the model reported BUSY for more than 1.5 s but
    // had cleared by the 3 s retry (36 of 36 waits), so these delays cover back-to-back requests.
    private val busyRetryDelaysMillis: List<Long> = listOf(1_000L, 2_000L, 3_000L),
    private val nanoClock: () -> Long = System::nanoTime,
) {

    /** Set once the device is known not to support the on-device model, so later calls skip it. */
    private var permanentReason: FallbackReason? = null

    private sealed interface Attempt {
        data class Succeeded(val bullets: List<String>, val durationMillis: Long) : Attempt
        data class Failed(val reason: FallbackReason, val cause: Throwable? = null) : Attempt
    }

    /**
     * @param text The passages to summarize, already trimmed to the on-device limit when possible.
     * @param sources The chunks [text] was built from, passed through to the result.
     * @param topic The user's topic. Only the cloud summarizer can use it; the on-device model takes text only.
     * @throws OnDeviceSummarizationException only in [RoutingMode.ON_DEVICE_ONLY] when the on-device model fails.
     */
    suspend fun summarize(
        text: String,
        sources: List<RetrievedChunk>,
        mode: RoutingMode = RoutingMode.AUTO,
        topic: String? = null,
    ): NotesSummary {
        if (mode == RoutingMode.CLOUD_ONLY) return fromCloud(text, sources, topic, reason = null)

        return when (val attempt = attemptOnDevice(text)) {
            is Attempt.Succeeded -> NotesSummary(
                bullets = attempt.bullets,
                engine = SummaryEngine.ON_DEVICE,
                sources = sources,
                durationMillis = attempt.durationMillis,
            )
            is Attempt.Failed -> {
                if (mode == RoutingMode.ON_DEVICE_ONLY) {
                    throw OnDeviceSummarizationException(attempt.reason, cause = attempt.cause)
                }
                fromCloud(text, sources, topic, attempt.reason)
            }
        }
    }

    private suspend fun fromCloud(
        text: String,
        sources: List<RetrievedChunk>,
        topic: String?,
        reason: FallbackReason?,
    ): NotesSummary {
        val start = nanoClock()
        val bullets = cloud.summarize(text, topic)
        return NotesSummary(
            bullets = bullets,
            engine = SummaryEngine.CLOUD,
            fallbackReason = reason,
            sources = sources,
            durationMillis = millisSince(start),
        )
    }

    private fun millisSince(startNanos: Long): Long = (nanoClock() - startNanos) / 1_000_000

    private suspend fun attemptOnDevice(text: String): Attempt {
        permanentReason?.let { return Attempt.Failed(it) }

        val availability = try {
            onDevice.availability()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return failed(reasonOf(e), e)
        }
        when (availability) {
            OnDeviceAvailability.Available -> Unit
            OnDeviceAvailability.Downloadable -> return Attempt.Failed(FallbackReason.MODEL_NOT_DOWNLOADED)
            OnDeviceAvailability.Downloading -> return Attempt.Failed(FallbackReason.MODEL_DOWNLOADING)
            is OnDeviceAvailability.Unavailable -> return failed(availability.reason)
        }

        if (text.length < onDevice.minInputChars) return Attempt.Failed(FallbackReason.INPUT_TOO_SHORT)
        if (text.length > onDevice.maxInputChars) return Attempt.Failed(FallbackReason.INPUT_TOO_LARGE)

        var retries = 0
        while (true) {
            try {
                val start = nanoClock()
                val bullets = onDevice.summarize(text)
                return Attempt.Succeeded(bullets, millisSince(start))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = reasonOf(e)
                if (reason == FallbackReason.BUSY && retries < busyRetryDelaysMillis.size) {
                    delay(busyRetryDelaysMillis[retries++])
                    continue
                }
                return failed(reason, e)
            }
        }
    }

    private fun reasonOf(error: Exception): FallbackReason =
        (error as? OnDeviceSummarizationException)?.reason ?: FallbackReason.UNKNOWN

    private fun failed(reason: FallbackReason, cause: Throwable? = null): Attempt.Failed {
        if (reason == FallbackReason.UNSUPPORTED_DEVICE) permanentReason = reason
        return Attempt.Failed(reason, cause)
    }
}
