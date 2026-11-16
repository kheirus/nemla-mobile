package app.nemla.feature.voice

import app.cash.turbine.test
import app.nemla.core.audio.MicrophoneAccess
import app.nemla.core.audio.RecorderState
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.usecase.GrantAiConsent
import app.nemla.core.domain.usecase.ObserveAiConsents
import app.nemla.core.domain.usecase.TranscribeSpeech
import app.nemla.feature.voice.common.VoiceMessage
import app.nemla.feature.voice.dictation.DictationAction
import app.nemla.feature.voice.dictation.DictationEffect
import app.nemla.feature.voice.dictation.DictationPhase
import app.nemla.feature.voice.dictation.DictationViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DictationViewModelTest : MainDispatcherTest() {
    private val factory = FakeRecorderFactory()
    private val recorder get() = factory.recorder
    private val microphone = FakeMicrophone()
    private val consents = FakeConsents(setOf(AiConsentLevel.PERSONAL))
    private val transcriber = FakeTranscriber()

    private fun viewModel() =
        DictationViewModel(
            recorderFactory = factory,
            microphone = microphone,
            observeConsents = ObserveAiConsents(consents),
            grantConsent = GrantAiConsent(consents),
            transcribe = TranscribeSpeech(transcriber),
            language = { AppLanguage.EN },
        )

    @Test
    fun recordingThenStoppingTranscribesInTheDictationLanguageAndWipesTheAudio() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.onAction(DictationAction.ButtonClicked)
                assertEquals(1, recorder.starts)
                recorder.mutableState.value = RecorderState.Recording(4, 30)
                assertEquals(DictationPhase.Recording(4, 30), vm.state.value.phase)
                assertTrue(vm.state.value.isActive)
                vm.onAction(DictationAction.ButtonClicked)
                assertEquals(1, recorder.stops)
                recorder.finish()
                val effect = awaitItem()
                assertTrue(effect is DictationEffect.Transcribed)
                assertEquals("loyer 750 euros", effect.text)
                assertEquals(listOf(AppLanguage.EN), transcriber.languages)
                assertTrue(transcriber.received.single().all { it == 0.toByte() })
                assertEquals(DictationPhase.Idle, vm.state.value.phase)
            }
        }

    @Test
    fun missingConsentAsksFirstAndDeclineSendsNothing() =
        runTest {
            consents.state.value = consents.state.value.copy(grants = emptyMap())
            val vm = viewModel()
            vm.onAction(DictationAction.ButtonClicked)
            assertTrue(vm.state.value.isConsentPromptVisible)
            assertEquals(0, recorder.starts)
            vm.onAction(DictationAction.ConsentDeclined)
            assertFalse(vm.state.value.isConsentPromptVisible)
            assertEquals(VoiceMessage.CONSENT_DECLINED, vm.state.value.message)
            vm.onAction(DictationAction.MessageShown)
            vm.onAction(DictationAction.ButtonClicked)
            vm.onAction(DictationAction.ConsentAccepted)
            assertEquals(listOf(AiConsentLevel.PERSONAL), consents.grants)
            assertEquals(1, recorder.starts)
        }

    @Test
    fun microphoneIsRequestedWhenNotGranted() =
        runTest {
            microphone.access = MicrophoneAccess.UNDETERMINED
            val vm = viewModel()
            vm.effects.test {
                vm.onAction(DictationAction.ButtonClicked)
                assertEquals(DictationEffect.RequestMicrophonePermission, awaitItem())
                vm.onAction(DictationAction.MicrophonePermissionResult(false))
                assertEquals(VoiceMessage.PERMISSION_DENIED, vm.state.value.message)
                vm.onAction(DictationAction.MicrophonePermissionResult(true))
                assertEquals(1, recorder.starts)
            }
        }

    @Test
    fun failuresBecomeMessagesAndBackgroundCancels() =
        runTest {
            transcriber.results += AppResult.Failure(AppError.Server(500))
            transcriber.results += AppResult.Failure(AppError.Validation(ValidationCode.NO_SPEECH_DETECTED))
            val vm = viewModel()
            vm.onAction(DictationAction.ButtonClicked)
            recorder.finish()
            assertEquals(VoiceMessage.TRANSCRIPTION_FAILED, vm.state.value.message)
            vm.onAction(DictationAction.MessageShown)
            vm.onAction(DictationAction.ButtonClicked)
            recorder.finish()
            assertEquals(VoiceMessage.NO_SPEECH, vm.state.value.message)

            vm.onAction(DictationAction.ButtonClicked)
            vm.onAction(DictationAction.AppBackgrounded)
            assertEquals(DictationPhase.Idle, vm.state.value.phase)
            assertEquals(VoiceMessage.INTERRUPTED, vm.state.value.message)
        }

    @Test
    fun consentRequiredByTheRouterKeepsTheRecordingUntilAccepted() =
        runTest {
            transcriber.results += AppResult.Failure(AppError.ConsentRequired)
            val vm = viewModel()
            vm.effects.test {
                vm.onAction(DictationAction.ButtonClicked)
                recorder.finish()
                assertTrue(vm.state.value.isConsentPromptVisible)
                vm.onAction(DictationAction.ConsentAccepted)
                assertTrue(awaitItem() is DictationEffect.Transcribed)
                assertEquals(2, transcriber.languages.size)
            }
        }
}
