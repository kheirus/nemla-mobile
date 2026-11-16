package app.nemla.feature.voice

import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.usecase.CaptureVoiceExpenses
import app.nemla.core.domain.usecase.ConfirmReviewedExpenses
import app.nemla.core.domain.usecase.DeletePendingExpense
import app.nemla.core.domain.usecase.FindLearnedExpenses
import app.nemla.core.domain.usecase.GetMonthlyQuota
import app.nemla.core.domain.usecase.GrantAiConsent
import app.nemla.core.domain.usecase.ListCategories
import app.nemla.core.domain.usecase.ObserveAiConsents
import app.nemla.core.domain.usecase.ObserveAuthState
import app.nemla.feature.voice.capture.VoiceCaptureViewModel
import app.nemla.feature.voice.review.VoiceReviewViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.onClose

val featureVoiceModule: Module =
    module {
        factoryOf(::ObserveAiConsents)
        factoryOf(::GrantAiConsent)
        factoryOf(::CaptureVoiceExpenses)
        factoryOf(::GetMonthlyQuota)
        factoryOf(::ListCategories)
        factoryOf(::FindLearnedExpenses)
        factoryOf(::ConfirmReviewedExpenses)
        factoryOf(::DeletePendingExpense)
        factory { ObserveAuthState(get()) }
        single<DictationLanguage> { SystemDictationLanguage }
        single<Today> { SystemToday }
        single { VoiceReviewStore(get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) } onClose { it?.close() }
        viewModelOf(::VoiceCaptureViewModel)
        viewModel {
            VoiceReviewViewModel(
                store = get(),
                listCategories = get(),
                findLearned = get(),
                confirmReviewed = get(),
                deletePending = get(),
                today = get(),
                decimalSeparator = if (get<DictationLanguage>().current() == AppLanguage.EN) '.' else ',',
            )
        }
    }
