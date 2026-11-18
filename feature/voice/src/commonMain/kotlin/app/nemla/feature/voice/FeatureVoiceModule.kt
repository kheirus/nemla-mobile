package app.nemla.feature.voice

import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.port.PendingReviewOpener
import app.nemla.core.domain.usecase.CaptureVoiceExpenses
import app.nemla.core.domain.usecase.ConfirmReviewedExpenses
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.DeletePendingExpense
import app.nemla.core.domain.usecase.FindLearnedExpenses
import app.nemla.core.domain.usecase.GetMonthlyQuota
import app.nemla.core.domain.usecase.GrantAiConsent
import app.nemla.core.domain.usecase.ListCategories
import app.nemla.core.domain.usecase.ObserveAiConsents
import app.nemla.core.domain.usecase.ObserveAuthState
import app.nemla.core.domain.usecase.RefreshScheduledNotifications
import app.nemla.core.domain.usecase.TranscribeSpeech
import app.nemla.feature.voice.capture.VoiceCaptureViewModel
import app.nemla.feature.voice.dictation.DictationViewModel
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
        factory { RefreshScheduledNotifications(notifier = get(), profiles = get(), expenses = get(), languages = get()) }
        factoryOf(::ConfirmReviewedExpenses)
        factoryOf(::DeletePendingExpense)
        factoryOf(::TranscribeSpeech)
        factoryOf(::CurrentLanguages)
        single<DictationLanguage> { PreferredDictationLanguage(get()) }
        single<Today> { SystemToday }
        single { VoiceReviewStore(ObserveAuthState(get()), CoroutineScope(SupervisorJob() + Dispatchers.Default)) } onClose { it?.close() }
        single<PendingReviewOpener> { StorePendingReviewOpener(get()) }
        viewModelOf(::VoiceCaptureViewModel)
        viewModelOf(::DictationViewModel)
        viewModel {
            VoiceReviewViewModel(
                store = get(),
                listCategories = get(),
                findLearned = get(),
                confirmReviewed = get(),
                deletePending = get(),
                today = get(),
                decimalSeparator = if (get<CurrentLanguages>().ui() == AppLanguage.EN) '.' else ',',
            )
        }
    }
