package app.nemla.feature.voice.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.audio.MicrophoneAccess
import app.nemla.core.audio.MicrophonePermission
import app.nemla.core.audio.RecorderState
import app.nemla.core.audio.VoiceRecorderFactory
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.VoiceRecording
import app.nemla.core.domain.usecase.CaptureVoiceExpenses
import app.nemla.core.domain.usecase.GetMonthlyQuota
import app.nemla.core.domain.usecase.GrantAiConsent
import app.nemla.core.domain.usecase.ObserveAiConsents
import app.nemla.feature.voice.DictationLanguage
import app.nemla.feature.voice.ReviewOutcome
import app.nemla.feature.voice.VoiceReviewStore
import app.nemla.feature.voice.common.NoticeKind
import app.nemla.feature.voice.common.VoiceMessage
import app.nemla.feature.voice.common.VoiceNotice
import app.nemla.feature.voice.common.toMessage
import app.nemla.feature.voice.common.toVoiceMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class VoiceCaptureViewModel(
    recorderFactory: VoiceRecorderFactory,
    private val microphone: MicrophonePermission,
    private val observeConsents: ObserveAiConsents,
    private val grantConsent: GrantAiConsent,
    private val captureExpenses: CaptureVoiceExpenses,
    private val getQuota: GetMonthlyQuota,
    private val store: VoiceReviewStore,
    private val language: DictationLanguage,
) : ViewModel() {
    private val recorder = recorderFactory.create(viewModelScope)

    private val mutableState = MutableStateFlow(VoiceCaptureUiState())
    val state: StateFlow<VoiceCaptureUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<VoiceCaptureEffect>(Channel.BUFFERED)
    val effects: Flow<VoiceCaptureEffect> = effectChannel.receiveAsFlow()

    private var pendingRecording: VoiceRecording? = null
    private var startAfterConsent = false

    init {
        refreshQuota()
        viewModelScope.launch { recorder.state.collect(::onRecorderState) }
        viewModelScope.launch {
            store.pending.collect { capture ->
                mutableState.update { it.copy(hasPendingReview = capture?.drafts?.isNotEmpty() == true) }
            }
        }
        viewModelScope.launch {
            store.outcome.filterNotNull().collect {
                if (store.consumeOutcome() == ReviewOutcome.SAVED) {
                    mutableState.update { it.copy(notice = VoiceNotice(NoticeKind.Success, VoiceMessage.SAVED)) }
                }
            }
        }
    }

    fun onAction(action: VoiceCaptureAction) {
        when (action) {
            VoiceCaptureAction.FabClicked -> openMenu()
            VoiceCaptureAction.MenuDismissed -> mutableState.update { it.copy(isMenuOpen = false) }
            VoiceCaptureAction.DictateClicked -> openDialog()
            VoiceCaptureAction.ResumeReviewClicked -> resumeReview()
            VoiceCaptureAction.DialogDismissed -> closeDialog()
            VoiceCaptureAction.RecordButtonClicked -> toggleRecording()
            VoiceCaptureAction.ConsentAccepted -> acceptConsent()
            VoiceCaptureAction.ConsentDeclined -> declineConsent()
            is VoiceCaptureAction.MicrophonePermissionResult -> onMicrophonePermission(action.granted)
            VoiceCaptureAction.AppBackgrounded -> interruptRecording()
            VoiceCaptureAction.NoticeDismissed -> mutableState.update { it.copy(notice = null) }
        }
    }

    private fun openMenu() {
        mutableState.update { it.copy(isMenuOpen = true) }
        refreshQuota()
    }

    private fun openDialog() {
        if (!mutableState.value.canDictate) return
        mutableState.update { it.copy(isMenuOpen = false, isDialogOpen = true, dialogMessage = null, notice = null) }
    }

    private fun resumeReview() {
        mutableState.update { it.copy(isMenuOpen = false) }
        if (store.pending.value != null) emit(VoiceCaptureEffect.OpenReview)
    }

    private fun closeDialog() {
        if (!mutableState.value.isDialogDismissible) return
        mutableState.update { it.copy(isDialogOpen = false, dialogMessage = null) }
    }

    private fun toggleRecording() {
        when (mutableState.value.phase) {
            CapturePhase.Idle -> requestStart()
            is CapturePhase.Recording -> recorder.stop()
            CapturePhase.Processing -> Unit
        }
    }

    private fun requestStart() {
        val current = mutableState.value
        if (current.isConsentPromptVisible) return
        if (!current.canDictate) {
            mutableState.update { it.copy(dialogMessage = VoiceMessage.QUOTA_REACHED) }
            return
        }
        mutableState.update { it.copy(dialogMessage = null) }
        viewModelScope.launch {
            if (observeConsents().first().isGranted(AiConsentLevel.PERSONAL)) {
                ensureMicrophone()
            } else {
                startAfterConsent = true
                mutableState.update { it.copy(isConsentPromptVisible = true) }
            }
        }
    }

    private fun ensureMicrophone() {
        if (microphone.status() == MicrophoneAccess.GRANTED) {
            recorder.start()
        } else {
            emit(VoiceCaptureEffect.RequestMicrophonePermission)
        }
    }

    private fun onMicrophonePermission(granted: Boolean) {
        if (granted) {
            recorder.start()
        } else {
            mutableState.update { it.copy(dialogMessage = VoiceMessage.PERMISSION_DENIED) }
        }
    }

    private fun acceptConsent() {
        mutableState.update { it.copy(isConsentPromptVisible = false) }
        viewModelScope.launch {
            if (grantConsent(AiConsentLevel.PERSONAL) is AppResult.Failure) {
                startAfterConsent = false
                discardPendingRecording()
                report(VoiceMessage.PROCESSING_FAILED)
                return@launch
            }
            val recording = pendingRecording
            when {
                recording != null -> {
                    pendingRecording = null
                    process(recording)
                }

                startAfterConsent -> {
                    startAfterConsent = false
                    ensureMicrophone()
                }
            }
        }
    }

    private fun declineConsent() {
        startAfterConsent = false
        discardPendingRecording()
        mutableState.update { it.copy(isConsentPromptVisible = false, phase = CapturePhase.Idle) }
        report(VoiceMessage.CONSENT_DECLINED, NoticeKind.Info)
    }

    private fun interruptRecording() {
        if (mutableState.value.phase !is CapturePhase.Recording) return
        recorder.cancel()
        mutableState.update { it.copy(phase = CapturePhase.Idle, dialogMessage = VoiceMessage.INTERRUPTED) }
    }

    private fun onRecorderState(recorderState: RecorderState) {
        when (recorderState) {
            RecorderState.Idle -> {
                mutableState.update { if (it.phase is CapturePhase.Recording) it.copy(phase = CapturePhase.Idle) else it }
            }

            is RecorderState.Recording -> {
                mutableState.update {
                    it.copy(
                        phase = CapturePhase.Recording(recorderState.elapsedSeconds, recorderState.maxSeconds),
                        dialogMessage = null,
                    )
                }
            }

            is RecorderState.Finished -> {
                val recording = VoiceRecording(recorderState.wavBytes, recorderState.sampleRateHz, recorderState.durationMillis)
                recorder.cancel()
                process(recording)
            }

            is RecorderState.Failed -> {
                mutableState.update { it.copy(phase = CapturePhase.Idle, dialogMessage = recorderState.error.toMessage()) }
            }
        }
    }

    private fun process(recording: VoiceRecording) {
        mutableState.update { it.copy(phase = CapturePhase.Processing, dialogMessage = null) }
        viewModelScope.launch {
            val result = captureExpenses(recording, language.current())
            if (result is AppResult.Failure && result.error == AppError.ConsentRequired) {
                pendingRecording = recording
                mutableState.update { it.copy(phase = CapturePhase.Idle, isConsentPromptVisible = true) }
                return@launch
            }
            recording.wavBytes.fill(0)
            when (result) {
                is AppResult.Success -> {
                    val capture = result.value
                    if (capture.drafts.isEmpty()) {
                        finishWithError(VoiceMessage.NO_EXPENSE)
                    } else {
                        store.put(capture)
                        mutableState.update { it.copy(phase = CapturePhase.Idle, isDialogOpen = false, dialogMessage = null) }
                        effectChannel.send(VoiceCaptureEffect.OpenReview)
                    }
                }

                is AppResult.Failure -> {
                    finishWithError(result.error.toVoiceMessage())
                }
            }
            refreshQuota()
        }
    }

    private fun finishWithError(message: VoiceMessage) {
        mutableState.update { it.copy(phase = CapturePhase.Idle) }
        report(message)
    }

    private fun report(
        message: VoiceMessage,
        kind: NoticeKind = NoticeKind.Error,
    ) {
        mutableState.update {
            if (it.isDialogOpen) it.copy(dialogMessage = message) else it.copy(notice = VoiceNotice(kind, message))
        }
    }

    private fun discardPendingRecording() {
        pendingRecording?.wavBytes?.fill(0)
        pendingRecording = null
    }

    private fun refreshQuota() {
        viewModelScope.launch {
            val status =
                when (val result = getQuota()) {
                    is AppResult.Success -> {
                        val quota = result.value
                        if (quota.canDictate) QuotaStatus.Available(quota.voiceRemaining) else QuotaStatus.Exhausted
                    }

                    is AppResult.Failure -> {
                        null
                    }
                }
            mutableState.update { current ->
                val next = status ?: if (current.quota == QuotaStatus.Loading) QuotaStatus.Unknown else current.quota
                current.copy(quota = next)
            }
        }
    }

    private fun emit(effect: VoiceCaptureEffect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }

    override fun onCleared() {
        recorder.cancel()
        discardPendingRecording()
    }
}
