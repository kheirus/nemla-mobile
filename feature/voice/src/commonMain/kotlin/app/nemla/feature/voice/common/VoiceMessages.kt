package app.nemla.feature.voice.common

import app.nemla.core.audio.RecordingError
import app.nemla.core.common.AppError
import app.nemla.core.common.ValidationCode

internal enum class VoiceMessage {
    PERMISSION_DENIED,
    TOO_SHORT,
    MICROPHONE_UNAVAILABLE,
    QUOTA_REACHED,
    NO_SPEECH,
    NO_EXPENSE,
    TOO_LARGE,
    NO_CATEGORY,
    OFFLINE,
    TIMEOUT,
    RATE_LIMITED,
    PROCESSING_FAILED,
    CONSENT_DECLINED,
    INTERRUPTED,
    SAVED,
}

internal enum class NoticeKind { Error, Info, Success }

internal data class VoiceNotice(
    val kind: NoticeKind,
    val message: VoiceMessage,
)

internal fun RecordingError.toMessage(): VoiceMessage =
    when (this) {
        RecordingError.PERMISSION_DENIED -> VoiceMessage.PERMISSION_DENIED
        RecordingError.TOO_SHORT -> VoiceMessage.TOO_SHORT
        RecordingError.MICROPHONE_UNAVAILABLE -> VoiceMessage.MICROPHONE_UNAVAILABLE
    }

internal fun AppError.toVoiceMessage(): VoiceMessage =
    when (this) {
        AppError.QuotaExceeded -> VoiceMessage.QUOTA_REACHED
        AppError.Network.Offline -> VoiceMessage.OFFLINE
        AppError.Network.Timeout -> VoiceMessage.TIMEOUT
        AppError.RateLimited -> VoiceMessage.RATE_LIMITED
        is AppError.Validation -> code.toVoiceMessage()
        else -> VoiceMessage.PROCESSING_FAILED
    }

private fun ValidationCode.toVoiceMessage(): VoiceMessage =
    when (this) {
        ValidationCode.NO_SPEECH_DETECTED -> VoiceMessage.NO_SPEECH
        ValidationCode.NO_EXPENSE_DETECTED -> VoiceMessage.NO_EXPENSE
        ValidationCode.PAYLOAD_TOO_LARGE -> VoiceMessage.TOO_LARGE
        ValidationCode.NO_CATEGORY -> VoiceMessage.NO_CATEGORY
        else -> VoiceMessage.PROCESSING_FAILED
    }
