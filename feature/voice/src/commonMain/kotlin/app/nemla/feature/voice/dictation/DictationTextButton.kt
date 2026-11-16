package app.nemla.feature.voice.dictation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.nemla.core.audio.rememberMicrophonePermissionRequest
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.feature.voice.common.AiConsentDialog
import app.nemla.feature.voice.common.text
import app.nemla.feature.voice.resources.Res
import app.nemla.feature.voice.resources.dictation_start
import app.nemla.feature.voice.resources.dictation_stop
import app.nemla.feature.voice.resources.dictation_transcribing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DictationTextButton(
    enabled: Boolean,
    onTranscript: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DictationRoute(enabled, onTranscript, onActiveChange, onMessage, modifier)
}

@Composable
internal fun DictationRoute(
    enabled: Boolean,
    onTranscript: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DictationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnTranscript by rememberUpdatedState(onTranscript)
    val currentOnActiveChange by rememberUpdatedState(onActiveChange)
    val currentOnMessage by rememberUpdatedState(onMessage)
    val requestMicrophone =
        rememberMicrophonePermissionRequest { granted -> viewModel.onAction(DictationAction.MicrophonePermissionResult(granted)) }
    val currentRequestMicrophone by rememberUpdatedState(requestMicrophone)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    DictationEffect.RequestMicrophonePermission -> currentRequestMicrophone()
                    is DictationEffect.Transcribed -> currentOnTranscript(effect.text)
                }
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAction(DictationAction.AppBackgrounded) }
    LaunchedEffect(state.isActive) { currentOnActiveChange(state.isActive) }
    state.message?.let { message ->
        val text = message.text()
        LaunchedEffect(message) {
            currentOnMessage(text)
            viewModel.onAction(DictationAction.MessageShown)
        }
    }
    DictationButtonContent(state = state, enabled = enabled, onClick = { viewModel.onAction(DictationAction.ButtonClicked) }, modifier)
    if (state.isConsentPromptVisible) {
        AiConsentDialog(
            onAccept = { viewModel.onAction(DictationAction.ConsentAccepted) },
            onDecline = { viewModel.onAction(DictationAction.ConsentDeclined) },
        )
    }
}

@Composable
internal fun DictationButtonContent(
    state: DictationUiState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val phase = state.phase) {
        is DictationPhase.Recording -> {
            NemlaButton(
                text = stringResource(Res.string.dictation_stop, phase.elapsedSeconds, phase.maxSeconds),
                onClick = onClick,
                variant = NemlaButtonVariant.Destructive,
                leadingIcon = NemlaIcons.Square,
                modifier = modifier,
            )
        }

        DictationPhase.Transcribing -> {
            NemlaButton(
                text = stringResource(Res.string.dictation_transcribing),
                onClick = onClick,
                variant = NemlaButtonVariant.Secondary,
                loading = true,
                modifier = modifier,
            )
        }

        DictationPhase.Idle -> {
            NemlaButton(
                text = stringResource(Res.string.dictation_start),
                onClick = onClick,
                variant = NemlaButtonVariant.Secondary,
                leadingIcon = NemlaIcons.Mic,
                enabled = enabled,
                modifier = modifier,
            )
        }
    }
}
