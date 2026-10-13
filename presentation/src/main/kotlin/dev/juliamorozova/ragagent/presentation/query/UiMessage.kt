package dev.juliamorozova.ragagent.presentation.query

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.juliamorozova.ragagent.domain.model.FallbackReason
import dev.juliamorozova.ragagent.presentation.R

/**
 * Text as a string resource plus arguments, so the ViewModel needs no Context; an argument may itself be a
 * [UiMessage].
 */
data class UiMessage(@StringRes val resId: Int, val args: List<Any> = emptyList())

@Composable
fun UiMessage.asString(): String {
    val resolved = args.map { if (it is UiMessage) it.asString() else it }
    return stringResource(resId, *resolved.toTypedArray())
}

@StringRes
fun FallbackReason.toStringRes(): Int = when (this) {
    FallbackReason.UNSUPPORTED_DEVICE -> R.string.query_fallback_unsupported_device
    FallbackReason.SERVICE_UNAVAILABLE -> R.string.query_fallback_service_unavailable
    FallbackReason.MODEL_NOT_DOWNLOADED -> R.string.query_fallback_model_not_downloaded
    FallbackReason.MODEL_DOWNLOADING -> R.string.query_fallback_model_downloading
    FallbackReason.INPUT_TOO_SHORT -> R.string.query_fallback_input_too_short
    FallbackReason.INPUT_TOO_LARGE -> R.string.query_fallback_input_too_large
    FallbackReason.GENERATION_FAILED -> R.string.query_fallback_generation_failed
    FallbackReason.BUSY -> R.string.query_fallback_busy
    FallbackReason.NEEDS_SYSTEM_UPDATE -> R.string.query_fallback_needs_system_update
    FallbackReason.NOT_ENOUGH_DISK_SPACE -> R.string.query_fallback_not_enough_disk_space
    FallbackReason.UNKNOWN -> R.string.query_fallback_unknown
}
