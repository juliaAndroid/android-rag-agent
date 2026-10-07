package dev.juliamorozova.ragagent.rag.agent.nano

import android.content.Context
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.Summarizer
import com.google.mlkit.genai.summarization.SummarizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.juliamorozova.ragagent.domain.model.FallbackReason
import dev.juliamorozova.ragagent.domain.summary.OnDeviceAvailability
import dev.juliamorozova.ragagent.domain.summary.OnDeviceSummarizationException
import dev.juliamorozova.ragagent.domain.summary.OnDeviceSummarizer
import dev.juliamorozova.ragagent.domain.summary.parseBullets
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** Limits measured on a Galaxy S25 (ML Kit GenAI Summarization 1.0.0-beta1, ARTICLE input). */
private const val MIN_INPUT_CHARS = 400
private const val MAX_INPUT_CHARS = 7_860

/**
 * [OnDeviceSummarizer] backed by Gemini Nano through ML Kit GenAI Summarization.
 *
 * Uses the plain `runInference(request)` call, never the streaming overload: on the S25 the
 * streaming call failed with a policy-check error on input the plain call summarized fine.
 *
 * Every ML Kit failure leaves this class as an [OnDeviceSummarizationException] carrying a
 * [FallbackReason], so callers never depend on ML Kit's error codes.
 */
@Singleton
class NanoSummarizer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : OnDeviceSummarizer {

    override val minInputChars: Int = MIN_INPUT_CHARS
    override val maxInputChars: Int = MAX_INPUT_CHARS

    private val client: Summarizer by lazy {
        Summarization.getClient(
            SummarizerOptions.builder(context)
                .setInputType(SummarizerOptions.InputType.ARTICLE)
                .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
                .setLanguage(SummarizerOptions.Language.ENGLISH)
                .build(),
        )
    }

    override suspend fun availability(): OnDeviceAvailability = translating {
        availabilityFor(client.checkFeatureStatus().await()) ?: OnDeviceAvailability.Unavailable(unavailableReason())
    }

    override suspend fun downloadModel(onProgress: (percent: Int) -> Unit) = translating {
        var totalBytes = -1L
        client.downloadFeature(
            object : DownloadCallback {
                override fun onDownloadStarted(bytesToDownload: Long) {
                    totalBytes = bytesToDownload
                    onProgress(0)
                }

                override fun onDownloadProgress(totalBytesDownloaded: Long) {
                    if (totalBytes > 0) onProgress((totalBytesDownloaded * 100 / totalBytes).toInt().coerceIn(0, 100))
                }

                override fun onDownloadCompleted() = onProgress(100)

                // The future returned by downloadFeature fails too, and translating() turns that into the error.
                override fun onDownloadFailed(e: GenAiException) = Unit
            },
        ).await()
        Unit
    }

    override suspend fun summarize(text: String): List<String> = translating {
        val result = client.runInference(SummarizationRequest.builder(text).build()).await()
        parseBullets(result.summary).ifEmpty {
            throw OnDeviceSummarizationException(FallbackReason.GENERATION_FAILED, "The model returned no bullets")
        }
    }

    /**
     * [com.google.mlkit.genai.common.FeatureStatus.UNAVAILABLE] gives no reason, but on an unsupported
     * phone (a Galaxy S23) a follow-up call fails with the real error code, so ask for one.
     */
    private suspend fun unavailableReason(): FallbackReason = try {
        client.getBaseModelName().await()
        FallbackReason.UNSUPPORTED_DEVICE
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        reasonOf(e)
    }

    private suspend fun <T> translating(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: OnDeviceSummarizationException) {
        throw e
    } catch (e: Exception) {
        throw OnDeviceSummarizationException(reasonOf(e), cause = e)
    }

    private fun reasonOf(error: Throwable): FallbackReason =
        (error as? GenAiException)?.let { fallbackReasonFor(it.errorCode) } ?: FallbackReason.UNKNOWN
}
