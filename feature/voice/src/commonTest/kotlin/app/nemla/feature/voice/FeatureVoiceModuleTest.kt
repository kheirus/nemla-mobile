package app.nemla.feature.voice

import app.nemla.core.audio.MicrophonePermission
import app.nemla.core.audio.VoiceRecorderFactory
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.PendingReview
import app.nemla.core.domain.model.ReviewOrigin
import app.nemla.core.domain.port.AiConsentRepository
import app.nemla.core.domain.port.AuthRepository
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.domain.port.DeviceLanguage
import app.nemla.core.domain.port.ExpenseRepository
import app.nemla.core.domain.port.ExpenseReviewRepository
import app.nemla.core.domain.port.LocalNotifier
import app.nemla.core.domain.port.PendingReviewOpener
import app.nemla.core.domain.port.PreferencesRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.QuotaRepository
import app.nemla.core.domain.port.SpeechTranscriber
import app.nemla.core.domain.port.VoiceExpenseCapturer
import app.nemla.feature.voice.capture.VoiceCaptureViewModel
import app.nemla.feature.voice.dictation.DictationViewModel
import app.nemla.feature.voice.review.VoiceReviewViewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureVoiceModuleTest : MainDispatcherTest() {
    @Test
    fun viewModelsResolve() {
        val application =
            koinApplication {
                modules(
                    module {
                        single<VoiceRecorderFactory> { FakeRecorderFactory() }
                        single<MicrophonePermission> { FakeMicrophone() }
                        single<AiConsentRepository> { FakeConsents() }
                        single<VoiceExpenseCapturer> { FakeCapturer() }
                        single<QuotaRepository> { FakeQuotas() }
                        single<CategoryRepository> { FakeCategories() }
                        single<ExpenseReviewRepository> { FakeReviews() }
                        single<AuthRepository> { FakeAuth() }
                        single<SpeechTranscriber> { FakeTranscriber() }
                        single<PreferencesRepository> { FakePreferences() }
                        single<DeviceLanguage> { DeviceLanguage { AppLanguage.FR } }
                        single<LocalNotifier> { FakeNotifier() }
                        single<ProfileRepository> { UnreadableProfiles }
                        single<ExpenseRepository> { NoExpenses }
                    },
                    featureVoiceModule,
                )
            }
        try {
            application.koin.get<VoiceCaptureViewModel>()
            application.koin.get<DictationViewModel>()
            application.koin.get<PendingReviewOpener>().open(PendingReview(ReviewOrigin.IMPORT, null, listOf(draft("e-1", ""))))
            assertEquals(ReviewOrigin.IMPORT, application.koin.get<VoiceReviewStore>().origin)
            application.koin.get<VoiceReviewStore>().clear()
            assertTrue(
                application.koin
                    .get<VoiceReviewViewModel>()
                    .state.value.lines
                    .isEmpty(),
            )
        } finally {
            application.close()
        }
    }
}
