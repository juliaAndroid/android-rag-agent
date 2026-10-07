package dev.juliamorozova.ragagent.rag.agent.nano

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import dev.juliamorozova.ragagent.domain.model.FallbackReason
import dev.juliamorozova.ragagent.domain.summary.OnDeviceAvailability

/** "AiCore service failed to bind": seen on a Galaxy S25 while AICore was disabled in settings. Not in [GenAiException.ErrorCode]. */
internal const val ERROR_BINDING_FAILURE = 601

/**
 * Translates an ML Kit GenAI error code into the app's own [FallbackReason], so the router never
 * sees library-specific numbers. Only the "device doesn't support Nano" case is permanent; every
 * other reason may clear up, so it must not be mapped to [FallbackReason.UNSUPPORTED_DEVICE].
 */
internal fun fallbackReasonFor(errorCode: Int): FallbackReason = when (errorCode) {
    GenAiException.ErrorCode.AICORE_INCOMPATIBLE -> FallbackReason.UNSUPPORTED_DEVICE
    ERROR_BINDING_FAILURE,
    // Not seen on a device yet; treated as fixable so a wrong guess can't lock Nano out for the session.
    GenAiException.ErrorCode.NOT_AVAILABLE,
    -> FallbackReason.SERVICE_UNAVAILABLE
    GenAiException.ErrorCode.BUSY -> FallbackReason.BUSY
    GenAiException.ErrorCode.REQUEST_TOO_SMALL -> FallbackReason.INPUT_TOO_SHORT
    GenAiException.ErrorCode.REQUEST_TOO_LARGE -> FallbackReason.INPUT_TOO_LARGE
    GenAiException.ErrorCode.REQUEST_PROCESSING_ERROR,
    GenAiException.ErrorCode.RESPONSE_PROCESSING_ERROR,
    GenAiException.ErrorCode.RESPONSE_GENERATION_ERROR,
    -> FallbackReason.GENERATION_FAILED
    GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE -> FallbackReason.NEEDS_SYSTEM_UPDATE
    GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> FallbackReason.NOT_ENOUGH_DISK_SPACE
    else -> FallbackReason.UNKNOWN
}

/**
 * Maps a feature status to an availability, or null for [FeatureStatus.UNAVAILABLE]: that status
 * carries no reason, so the caller has to find one.
 */
internal fun availabilityFor(featureStatus: Int): OnDeviceAvailability? = when (featureStatus) {
    FeatureStatus.AVAILABLE -> OnDeviceAvailability.Available
    FeatureStatus.DOWNLOADABLE -> OnDeviceAvailability.Downloadable
    FeatureStatus.DOWNLOADING -> OnDeviceAvailability.Downloading
    else -> null
}
