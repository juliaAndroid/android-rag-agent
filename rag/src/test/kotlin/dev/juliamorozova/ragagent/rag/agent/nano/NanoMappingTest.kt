package dev.juliamorozova.ragagent.rag.agent.nano

import com.google.common.truth.Truth.assertThat
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import dev.juliamorozova.ragagent.domain.model.FallbackReason
import dev.juliamorozova.ragagent.domain.summary.OnDeviceAvailability
import org.junit.Test

class NanoMappingTest {

    @Test
    fun `an incompatible AICore means the device is unsupported`() {
        // What a Galaxy S23 and an emulator returned.
        assertThat(fallbackReasonFor(GenAiException.ErrorCode.AICORE_INCOMPATIBLE))
            .isEqualTo(FallbackReason.UNSUPPORTED_DEVICE)
    }

    @Test
    fun `a binding failure is a fixable service problem and not a permanent one`() {
        // What a Galaxy S25 returned while AICore was disabled in settings.
        assertThat(fallbackReasonFor(601)).isEqualTo(FallbackReason.SERVICE_UNAVAILABLE)
        assertThat(ERROR_BINDING_FAILURE).isEqualTo(601)
    }

    @Test
    fun `input size errors map to the matching input reasons`() {
        assertThat(fallbackReasonFor(GenAiException.ErrorCode.REQUEST_TOO_SMALL))
            .isEqualTo(FallbackReason.INPUT_TOO_SHORT)
        assertThat(fallbackReasonFor(GenAiException.ErrorCode.REQUEST_TOO_LARGE))
            .isEqualTo(FallbackReason.INPUT_TOO_LARGE)
    }

    @Test
    fun `every generation failure maps to GENERATION_FAILED`() {
        listOf(
            GenAiException.ErrorCode.REQUEST_PROCESSING_ERROR,
            GenAiException.ErrorCode.RESPONSE_PROCESSING_ERROR, // the policy-check error seen with streaming
            GenAiException.ErrorCode.RESPONSE_GENERATION_ERROR, // "Couldn't generate a response"
        ).forEach { code ->
            assertThat(fallbackReasonFor(code)).isEqualTo(FallbackReason.GENERATION_FAILED)
        }
    }

    @Test
    fun `busy, update and disk errors keep their own reasons`() {
        assertThat(fallbackReasonFor(GenAiException.ErrorCode.BUSY)).isEqualTo(FallbackReason.BUSY)
        assertThat(fallbackReasonFor(GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE))
            .isEqualTo(FallbackReason.NEEDS_SYSTEM_UPDATE)
        assertThat(fallbackReasonFor(GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE))
            .isEqualTo(FallbackReason.NOT_ENOUGH_DISK_SPACE)
    }

    @Test
    fun `NOT_AVAILABLE is treated as fixable so it cannot lock Nano out for the session`() {
        assertThat(fallbackReasonFor(GenAiException.ErrorCode.NOT_AVAILABLE))
            .isEqualTo(FallbackReason.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `only the incompatible AICore error is permanent`() {
        val permanent = (-200..700).filter { fallbackReasonFor(it) == FallbackReason.UNSUPPORTED_DEVICE }

        assertThat(permanent).containsExactly(GenAiException.ErrorCode.AICORE_INCOMPATIBLE)
    }

    @Test
    fun `an unknown code maps to UNKNOWN`() {
        assertThat(fallbackReasonFor(GenAiException.ErrorCode.UNKNOWN)).isEqualTo(FallbackReason.UNKNOWN)
        assertThat(fallbackReasonFor(987_654)).isEqualTo(FallbackReason.UNKNOWN)
    }

    @Test
    fun `feature statuses map to availability`() {
        assertThat(availabilityFor(FeatureStatus.AVAILABLE)).isEqualTo(OnDeviceAvailability.Available)
        assertThat(availabilityFor(FeatureStatus.DOWNLOADABLE)).isEqualTo(OnDeviceAvailability.Downloadable)
        assertThat(availabilityFor(FeatureStatus.DOWNLOADING)).isEqualTo(OnDeviceAvailability.Downloading)
    }

    @Test
    fun `UNAVAILABLE has no reason of its own so the caller must look for one`() {
        assertThat(availabilityFor(FeatureStatus.UNAVAILABLE)).isNull()
    }
}
