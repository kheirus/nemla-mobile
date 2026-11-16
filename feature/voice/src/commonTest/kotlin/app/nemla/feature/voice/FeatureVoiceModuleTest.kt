package app.nemla.feature.voice

import app.nemla.core.audio.MicrophonePermission
import app.nemla.core.audio.VoiceRecorderFactory
import app.nemla.core.domain.port.AiConsentRepository
import app.nemla.core.domain.port.AuthRepository
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.domain.port.ExpenseReviewRepository
import app.nemla.core.domain.port.QuotaRepository
import app.nemla.core.domain.port.VoiceExpenseCapturer
import app.nemla.feature.voice.capture.VoiceCaptureViewModel
import app.nemla.feature.voice.review.VoiceReviewViewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
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
                    },
                    featureVoiceModule,
                )
            }
        try {
            application.koin.get<VoiceCaptureViewModel>()
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
