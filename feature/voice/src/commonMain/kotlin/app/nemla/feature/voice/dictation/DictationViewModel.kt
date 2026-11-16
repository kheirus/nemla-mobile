package app.nemla.feature.voice.dictation

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
import app.nemla.core.domain.usecase.GrantAiConsent
import app.nemla.core.domain.usecase.ObserveAiConsents
import app.nemla.core.domain.usecase.TranscribeSpeech
import app.nemla.feature.voice.DictationLanguage
import app.nemla.feature.voice.common.VoiceMessage
import app.nemla.feature.voice.common.toMessage
import app.nemla.feature.voice.common.toVoiceMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface DictationPhase {
    data object Idle : DictationPhase

    data class Recording(
        val elapsedSeconds: Int,
        val maxSeconds: Int,
    ) : DictationPhase

    data object Transcribing : DictationPhase
}

internal data class DictationUiState(
    val phase: DictationPhase = DictationPhase.Idle,
    val isConsentPromptVisible: Boolean = false,
    val message: VoiceMessage? = null,
) {
    val isActive: Boolean get() = phase != DictationPhase.Idle
}

internal sealed interface DictationAction {
    data object ButtonClicked : DictationAction

    data object ConsentAccepted : DictationAction

    data object ConsentDeclined : DictationAction

    data class MicrophonePermissionResult(
        val granted: Boolean,
    ) : DictationAction

    data object AppBackgrounded : DictationAction

    data object MessageShown : DictationAction
}

internal sealed interface DictationEffect {
    data object RequestMicrophonePermission : DictationEffect

    class Transcribed(
        val text: String,
    ) : DictationEffect {
        override fun toString(): String = "Transcribed(***)"
    }
}

internal class DictationViewModel(
    recorderFactory: VoiceRecorderFactory,
    private val microphone: MicrophonePermission,
    private val observeConsents: ObserveAiConsents,
    private val grantConsent: GrantAiConsent,
    private val transcribe: TranscribeSpeech,
    private val language: DictationLanguage,
) : ViewModel() {
    private val recorder = recorderFactory.create(viewModelScope)

    private val mutableState = MutableStateFlow(DictationUiState())
    val state: StateFlow<DictationUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<DictationEffect>(Channel.BUFFERED)
    val effects: Flow<DictationEffect> = effectChannel.receiveAsFlow()

    private var pendingRecording: VoiceRecording? = null

    init {
        viewModelScope.launch { recorder.state.collect(::onRecorderState) }
    }

    fun onAction(action: DictationAction) {
        when (action) {
            DictationAction.ButtonClicked -> toggle()
            DictationAction.ConsentAccepted -> acceptConsent()
            DictationAction.ConsentDeclined -> declineConsent()
            is DictationAction.MicrophonePermissionResult -> onMicrophonePermission(action.granted)
            DictationAction.AppBackgrounded -> interrupt()
            DictationAction.MessageShown -> mutableState.update { it.copy(message = null) }
        }
    }

    private fun toggle() {
        when (mutableState.value.phase) {
            DictationPhase.Idle -> requestStart()
            is DictationPhase.Recording -> recorder.stop()
            DictationPhase.Transcribing -> Unit
        }
    }

    private fun requestStart() {
        if (mutableState.value.isConsentPromptVisible) return
        viewModelScope.launch {
            if (observeConsents().first().isGranted(AiConsentLevel.PERSONAL)) {
                ensureMicrophone()
            } else {
                mutableState.update { it.copy(isConsentPromptVisible = true) }
            }
        }
    }

    private fun ensureMicrophone() {
        if (microphone.status() == MicrophoneAccess.GRANTED) {
            recorder.start()
        } else {
            viewModelScope.launch { effectChannel.send(DictationEffect.RequestMicrophonePermission) }
        }
    }

    private fun onMicrophonePermission(granted: Boolean) {
        if (granted) recorder.start() else mutableState.update { it.copy(message = VoiceMessage.PERMISSION_DENIED) }
    }

    private fun acceptConsent() {
        mutableState.update { it.copy(isConsentPromptVisible = false) }
        viewModelScope.launch {
            if (grantConsent(AiConsentLevel.PERSONAL) is AppResult.Failure) {
                discardPending()
                mutableState.update { it.copy(message = VoiceMessage.TRANSCRIPTION_FAILED) }
                return@launch
            }
            val recording = pendingRecording
            if (recording != null) {
                pendingRecording = null
                process(recording)
            } else {
                ensureMicrophone()
            }
        }
    }

    private fun declineConsent() {
        discardPending()
        mutableState.update {
            it.copy(isConsentPromptVisible = false, phase = DictationPhase.Idle, message = VoiceMessage.CONSENT_DECLINED)
        }
    }

    private fun interrupt() {
        if (mutableState.value.phase !is DictationPhase.Recording) return
        recorder.cancel()
        mutableState.update { it.copy(phase = DictationPhase.Idle, message = VoiceMessage.INTERRUPTED) }
    }

    private fun onRecorderState(recorderState: RecorderState) {
        when (recorderState) {
            RecorderState.Idle -> {
                mutableState.update { if (it.phase is DictationPhase.Recording) it.copy(phase = DictationPhase.Idle) else it }
            }

            is RecorderState.Recording -> {
                mutableState.update {
                    it.copy(phase = DictationPhase.Recording(recorderState.elapsedSeconds, recorderState.maxSeconds), message = null)
                }
            }

            is RecorderState.Finished -> {
                val recording = VoiceRecording(recorderState.wavBytes, recorderState.sampleRateHz, recorderState.durationMillis)
                recorder.cancel()
                process(recording)
            }

            is RecorderState.Failed -> {
                mutableState.update { it.copy(phase = DictationPhase.Idle, message = recorderState.error.toMessage()) }
            }
        }
    }

    private fun process(recording: VoiceRecording) {
        mutableState.update { it.copy(phase = DictationPhase.Transcribing, message = null) }
        viewModelScope.launch {
            val result = transcribe(recording, language.current())
            if (result is AppResult.Failure && result.error == AppError.ConsentRequired) {
                pendingRecording = recording
                mutableState.update { it.copy(phase = DictationPhase.Idle, isConsentPromptVisible = true) }
                return@launch
            }
            recording.wavBytes.fill(0)
            when (result) {
                is AppResult.Success -> {
                    mutableState.update { it.copy(phase = DictationPhase.Idle) }
                    effectChannel.send(DictationEffect.Transcribed(result.value))
                }

                is AppResult.Failure -> {
                    val message = result.error.toVoiceMessage().takeUnless { it == VoiceMessage.PROCESSING_FAILED }
                    mutableState.update { it.copy(phase = DictationPhase.Idle, message = message ?: VoiceMessage.TRANSCRIPTION_FAILED) }
                }
            }
        }
    }

    private fun discardPending() {
        pendingRecording?.wavBytes?.fill(0)
        pendingRecording = null
    }

    override fun onCleared() {
        recorder.cancel()
        discardPending()
    }
}
