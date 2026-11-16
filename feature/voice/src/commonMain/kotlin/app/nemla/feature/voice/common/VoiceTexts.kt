package app.nemla.feature.voice.common

import androidx.compose.runtime.Composable
import app.nemla.feature.voice.resources.Res
import app.nemla.feature.voice.resources.voice_consent_declined
import app.nemla.feature.voice.resources.voice_error_generic
import app.nemla.feature.voice.resources.voice_error_microphone
import app.nemla.feature.voice.resources.voice_error_no_category
import app.nemla.feature.voice.resources.voice_error_no_expense
import app.nemla.feature.voice.resources.voice_error_no_speech
import app.nemla.feature.voice.resources.voice_error_offline
import app.nemla.feature.voice.resources.voice_error_permission
import app.nemla.feature.voice.resources.voice_error_quota
import app.nemla.feature.voice.resources.voice_error_rate_limited
import app.nemla.feature.voice.resources.voice_error_timeout
import app.nemla.feature.voice.resources.voice_error_too_large
import app.nemla.feature.voice.resources.voice_error_too_short
import app.nemla.feature.voice.resources.voice_interrupted
import app.nemla.feature.voice.resources.voice_saved
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun VoiceMessage.text(): String = stringResource(resource())

private fun VoiceMessage.resource(): StringResource =
    when (this) {
        VoiceMessage.PERMISSION_DENIED -> Res.string.voice_error_permission
        VoiceMessage.TOO_SHORT -> Res.string.voice_error_too_short
        VoiceMessage.MICROPHONE_UNAVAILABLE -> Res.string.voice_error_microphone
        VoiceMessage.QUOTA_REACHED -> Res.string.voice_error_quota
        VoiceMessage.NO_SPEECH -> Res.string.voice_error_no_speech
        VoiceMessage.NO_EXPENSE -> Res.string.voice_error_no_expense
        VoiceMessage.TOO_LARGE -> Res.string.voice_error_too_large
        VoiceMessage.NO_CATEGORY -> Res.string.voice_error_no_category
        VoiceMessage.OFFLINE -> Res.string.voice_error_offline
        VoiceMessage.TIMEOUT -> Res.string.voice_error_timeout
        VoiceMessage.RATE_LIMITED -> Res.string.voice_error_rate_limited
        VoiceMessage.PROCESSING_FAILED -> Res.string.voice_error_generic
        VoiceMessage.CONSENT_DECLINED -> Res.string.voice_consent_declined
        VoiceMessage.INTERRUPTED -> Res.string.voice_interrupted
        VoiceMessage.SAVED -> Res.string.voice_saved
    }
