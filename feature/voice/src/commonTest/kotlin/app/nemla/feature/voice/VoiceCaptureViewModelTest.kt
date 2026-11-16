package app.nemla.feature.voice

import app.cash.turbine.test
import app.nemla.core.audio.MicrophoneAccess
import app.nemla.core.audio.RecorderState
import app.nemla.core.audio.RecordingError
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AiConsents
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.VoiceCapture
import app.nemla.core.domain.usecase.CaptureVoiceExpenses
import app.nemla.core.domain.usecase.GetMonthlyQuota
import app.nemla.core.domain.usecase.GrantAiConsent
import app.nemla.core.domain.usecase.ObserveAiConsents
import app.nemla.feature.voice.capture.CapturePhase
import app.nemla.feature.voice.capture.QuotaStatus
import app.nemla.feature.voice.capture.VoiceCaptureAction
import app.nemla.feature.voice.capture.VoiceCaptureEffect
import app.nemla.feature.voice.capture.VoiceCaptureViewModel
import app.nemla.feature.voice.common.NoticeKind
import app.nemla.feature.voice.common.VoiceMessage
import app.nemla.feature.voice.common.VoiceNotice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceCaptureViewModelTest : MainDispatcherTest() {
    private val factory = FakeRecorderFactory()
    private val recorder get() = factory.recorder
    private val microphone = FakeMicrophone()
    private val consents = FakeConsents(setOf(AiConsentLevel.PERSONAL))
    private val capturer = FakeCapturer()
    private val quotas = FakeQuotas()

    private fun TestScope.viewModel(store: VoiceReviewStore = testStore(scope = backgroundScope)) =
        VoiceCaptureViewModel(
            recorderFactory = factory,
            microphone = microphone,
            observeConsents = ObserveAiConsents(consents),
            grantConsent = GrantAiConsent(consents),
            captureExpenses = CaptureVoiceExpenses(capturer),
            getQuota = GetMonthlyQuota(quotas),
            store = store,
            language = { AppLanguage.FR },
        )

    private fun VoiceCaptureViewModel.openAndRecord() {
        onAction(VoiceCaptureAction.FabClicked)
        onAction(VoiceCaptureAction.DictateClicked)
        onAction(VoiceCaptureAction.RecordButtonClicked)
    }

    @Test
    fun remainingDictationsAreLoaded() =
        runTest {
            val vm = viewModel()
            assertEquals(QuotaStatus.Available(25), vm.state.value.quota)
            vm.onAction(VoiceCaptureAction.FabClicked)
            assertTrue(vm.state.value.isMenuOpen)
            assertEquals(2, quotas.calls)
        }

    @Test
    fun importEntryFollowsTheImportQuotaAndOpensTheImport() =
        runTest {
            val vm = viewModel()
            assertEquals(QuotaStatus.Available(1), vm.state.value.importQuota)
            vm.effects.test {
                vm.onAction(VoiceCaptureAction.FabClicked)
                vm.onAction(VoiceCaptureAction.ImportClicked)
                assertEquals(VoiceCaptureEffect.OpenImport, awaitItem())
            }
            assertFalse(vm.state.value.isMenuOpen)
        }

    @Test
    fun exhaustedImportQuotaDisablesTheImportEntry() =
        runTest {
            quotas.result = AppResult.Success(quota(used = 0, importUsed = 1))
            val vm = viewModel()
            assertEquals(QuotaStatus.Exhausted, vm.state.value.importQuota)
            assertFalse(vm.state.value.canImport)
            assertTrue(vm.state.value.canDictate)
            vm.effects.test {
                vm.onAction(VoiceCaptureAction.ImportClicked)
                expectNoEvents()
            }
        }

    @Test
    fun exhaustedCreditsDisableDictation() =
        runTest {
            quotas.result = AppResult.Success(quota(used = 30))
            val vm = viewModel()
            assertEquals(QuotaStatus.Exhausted, vm.state.value.quota)
            vm.onAction(VoiceCaptureAction.DictateClicked)
            assertFalse(vm.state.value.isDialogOpen)
        }

    @Test
    fun unreadableQuotaStillAllowsDictation() =
        runTest {
            quotas.result = AppResult.Failure(AppError.Network.Offline)
            val vm = viewModel()
            assertEquals(QuotaStatus.Unknown, vm.state.value.quota)
            vm.onAction(VoiceCaptureAction.DictateClicked)
            assertTrue(vm.state.value.isDialogOpen)
        }

    @Test
    fun firstDictationAsksForConsentBeforeRecording() =
        runTest {
            consents.state.value = AiConsents.None
            val vm = viewModel()
            vm.openAndRecord()
            assertTrue(vm.state.value.isConsentPromptVisible)
            assertEquals(0, recorder.starts)
            vm.onAction(VoiceCaptureAction.ConsentAccepted)
            assertEquals(listOf(AiConsentLevel.PERSONAL), consents.grants)
            assertFalse(vm.state.value.isConsentPromptVisible)
            assertEquals(1, recorder.starts)
        }

    @Test
    fun decliningConsentRecordsNothing() =
        runTest {
            consents.state.value = AiConsents.None
            val vm = viewModel()
            vm.openAndRecord()
            vm.onAction(VoiceCaptureAction.ConsentDeclined)
            assertEquals(0, recorder.starts)
            assertTrue(consents.grants.isEmpty())
            assertEquals(VoiceMessage.CONSENT_DECLINED, vm.state.value.dialogMessage)
            assertEquals(CapturePhase.Idle, vm.state.value.phase)
        }

    @Test
    fun missingMicrophonePermissionIsRequested() =
        runTest {
            microphone.access = MicrophoneAccess.UNDETERMINED
            val vm = viewModel()
            vm.effects.test {
                vm.openAndRecord()
                assertEquals(VoiceCaptureEffect.RequestMicrophonePermission, awaitItem())
            }
            vm.onAction(VoiceCaptureAction.MicrophonePermissionResult(granted = false))
            assertEquals(VoiceMessage.PERMISSION_DENIED, vm.state.value.dialogMessage)
            assertEquals(0, recorder.starts)
            vm.onAction(VoiceCaptureAction.MicrophonePermissionResult(granted = true))
            assertEquals(1, recorder.starts)
        }

    @Test
    fun recordingProgressIsShownAndTheDialogCannotBeClosed() =
        runTest {
            val vm = viewModel()
            vm.openAndRecord()
            recorder.mutableState.value = RecorderState.Recording(12)
            assertEquals(CapturePhase.Recording(12, 30), vm.state.value.phase)
            vm.onAction(VoiceCaptureAction.DialogDismissed)
            assertTrue(vm.state.value.isDialogOpen)
            vm.onAction(VoiceCaptureAction.RecordButtonClicked)
            assertEquals(1, recorder.stops)
        }

    @Test
    fun successfulDictationOpensTheReviewAndWipesTheAudio() =
        runTest {
            val store = testStore(scope = backgroundScope)
            val vm = viewModel(store)
            vm.openAndRecord()
            val audio = ByteArray(8_000) { 7 }
            vm.effects.test {
                recorder.finish(audio)
                assertEquals(VoiceCaptureEffect.OpenReview, awaitItem())
            }
            assertEquals(AppLanguage.FR, capturer.calls.single().second)
            assertTrue(capturer.received.single().all { it == 7.toByte() })
            assertTrue(audio.all { it == 0.toByte() })
            assertEquals(sampleCapture(), store.pending.value)
            assertFalse(vm.state.value.isDialogOpen)
            assertEquals(CapturePhase.Idle, vm.state.value.phase)
            assertTrue(vm.state.value.hasPendingReview)
            assertEquals(3, quotas.calls)
        }

    @Test
    fun serverErrorsAreTranslatedAndQuotaRefreshed() =
        runTest {
            val cases =
                listOf(
                    AppError.QuotaExceeded to VoiceMessage.QUOTA_REACHED,
                    AppError.Validation(ValidationCode.NO_SPEECH_DETECTED) to VoiceMessage.NO_SPEECH,
                    AppError.Validation(ValidationCode.NO_EXPENSE_DETECTED) to VoiceMessage.NO_EXPENSE,
                    AppError.Validation(ValidationCode.PAYLOAD_TOO_LARGE) to VoiceMessage.TOO_LARGE,
                    AppError.Network.Offline to VoiceMessage.OFFLINE,
                    AppError.Network.Timeout to VoiceMessage.TIMEOUT,
                    AppError.RateLimited to VoiceMessage.RATE_LIMITED,
                    AppError.Server(502) to VoiceMessage.PROCESSING_FAILED,
                )
            val vm = viewModel()
            vm.onAction(VoiceCaptureAction.DictateClicked)
            cases.forEachIndexed { index, (error, message) ->
                capturer.results.addLast(AppResult.Failure(error))
                vm.onAction(VoiceCaptureAction.RecordButtonClicked)
                recorder.finish()
                assertEquals(message, vm.state.value.dialogMessage, "case $index")
                assertEquals(CapturePhase.Idle, vm.state.value.phase)
                assertTrue(vm.state.value.isDialogOpen)
            }
            assertEquals(1 + cases.size, quotas.calls)
        }

    @Test
    fun anEmptyCaptureIsReportedAsNoExpense() =
        runTest {
            capturer.results.addLast(AppResult.Success(VoiceCapture(null, "", emptyList())))
            val vm = viewModel()
            vm.openAndRecord()
            recorder.finish()
            assertEquals(VoiceMessage.NO_EXPENSE, vm.state.value.dialogMessage)
        }

    @Test
    fun revokedConsentKeepsTheRecordingUntilTheUserAnswers() =
        runTest {
            capturer.results.addLast(AppResult.Failure(AppError.ConsentRequired))
            val vm = viewModel()
            vm.openAndRecord()
            recorder.finish(ByteArray(8_000) { 3 })
            assertTrue(vm.state.value.isConsentPromptVisible)
            vm.onAction(VoiceCaptureAction.ConsentAccepted)
            assertEquals(2, capturer.calls.size)
            assertTrue(capturer.received[1].all { it == 3.toByte() })
            assertFalse(vm.state.value.isConsentPromptVisible)
        }

    @Test
    fun recorderFailuresAreShownInTheDialog() =
        runTest {
            val vm = viewModel()
            vm.openAndRecord()
            recorder.mutableState.value = RecorderState.Failed(RecordingError.TOO_SHORT)
            assertEquals(VoiceMessage.TOO_SHORT, vm.state.value.dialogMessage)
            assertEquals(CapturePhase.Idle, vm.state.value.phase)
            assertTrue(capturer.calls.isEmpty())
        }

    @Test
    fun leavingTheAppDiscardsTheRecording() =
        runTest {
            val vm = viewModel()
            vm.openAndRecord()
            vm.onAction(VoiceCaptureAction.AppBackgrounded)
            assertEquals(1, recorder.cancels)
            assertEquals(VoiceMessage.INTERRUPTED, vm.state.value.dialogMessage)
            assertTrue(capturer.calls.isEmpty())
        }

    @Test
    fun processingContinuesAfterClosingTheDialogAndErrorsBecomeNotices() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            capturer.gate = gate
            capturer.results.addLast(AppResult.Failure(AppError.Network.Offline))
            val vm = viewModel()
            vm.openAndRecord()
            recorder.finish()
            assertEquals(CapturePhase.Processing, vm.state.value.phase)
            vm.onAction(VoiceCaptureAction.DialogDismissed)
            assertFalse(vm.state.value.isDialogOpen)
            gate.complete(Unit)
            assertEquals(VoiceNotice(NoticeKind.Error, VoiceMessage.OFFLINE), vm.state.value.notice)
            vm.onAction(VoiceCaptureAction.NoticeDismissed)
            assertNull(vm.state.value.notice)
        }

    @Test
    fun savedReviewShowsASuccessNotice() =
        runTest {
            val store = testStore(scope = backgroundScope)
            val vm = viewModel(store)
            store.publish(ReviewOutcome.SAVED)
            assertEquals(VoiceNotice(NoticeKind.Success, VoiceMessage.SAVED), vm.state.value.notice)
            assertNull(store.outcome.value)
        }

    @Test
    fun pendingReviewCanBeResumedFromTheMenu() =
        runTest {
            val store = testStore(scope = backgroundScope)
            val vm = viewModel(store)
            assertFalse(vm.state.value.hasPendingReview)
            store.put(sampleCapture())
            assertTrue(vm.state.value.hasPendingReview)
            vm.effects.test {
                vm.onAction(VoiceCaptureAction.ResumeReviewClicked)
                assertEquals(VoiceCaptureEffect.OpenReview, awaitItem())
            }
        }
}
