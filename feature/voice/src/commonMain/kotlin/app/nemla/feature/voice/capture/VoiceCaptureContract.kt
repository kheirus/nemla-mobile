package app.nemla.feature.voice.capture

import app.nemla.feature.voice.common.VoiceMessage
import app.nemla.feature.voice.common.VoiceNotice

internal sealed interface QuotaStatus {
    data object Loading : QuotaStatus

    data object Unknown : QuotaStatus

    data class Available(
        val remaining: Int,
    ) : QuotaStatus

    data object Exhausted : QuotaStatus
}

internal sealed interface CapturePhase {
    data object Idle : CapturePhase

    data class Recording(
        val elapsedSeconds: Int,
        val maxSeconds: Int,
    ) : CapturePhase

    data object Processing : CapturePhase
}

internal data class VoiceCaptureUiState(
    val quota: QuotaStatus = QuotaStatus.Loading,
    val isMenuOpen: Boolean = false,
    val isDialogOpen: Boolean = false,
    val phase: CapturePhase = CapturePhase.Idle,
    val dialogMessage: VoiceMessage? = null,
    val isConsentPromptVisible: Boolean = false,
    val notice: VoiceNotice? = null,
    val hasPendingReview: Boolean = false,
) {
    val canDictate: Boolean get() = quota != QuotaStatus.Exhausted

    val isDialogDismissible: Boolean get() = phase !is CapturePhase.Recording
}

internal sealed interface VoiceCaptureAction {
    data object FabClicked : VoiceCaptureAction

    data object MenuDismissed : VoiceCaptureAction

    data object DictateClicked : VoiceCaptureAction

    data object ResumeReviewClicked : VoiceCaptureAction

    data object DialogDismissed : VoiceCaptureAction

    data object RecordButtonClicked : VoiceCaptureAction

    data object ConsentAccepted : VoiceCaptureAction

    data object ConsentDeclined : VoiceCaptureAction

    data class MicrophonePermissionResult(
        val granted: Boolean,
    ) : VoiceCaptureAction

    data object AppBackgrounded : VoiceCaptureAction

    data object NoticeDismissed : VoiceCaptureAction
}

internal sealed interface VoiceCaptureEffect {
    data object RequestMicrophonePermission : VoiceCaptureEffect

    data object OpenReview : VoiceCaptureEffect
}
