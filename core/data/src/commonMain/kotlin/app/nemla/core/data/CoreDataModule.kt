package app.nemla.core.data

import app.nemla.ai.api.AiContextSource
import app.nemla.ai.api.AiEngine
import app.nemla.ai.api.AiRouter
import app.nemla.ai.api.AiRoutingPolicy
import app.nemla.ai.api.AiTraceListener
import app.nemla.ai.api.LocalFirstPolicy
import app.nemla.core.data.account.UnsupportedAccountRepository
import app.nemla.core.data.ai.AssumeOnlineConnectivityMonitor
import app.nemla.core.data.ai.ConnectivityMonitor
import app.nemla.core.data.ai.LoggingAiTraceListener
import app.nemla.core.data.ai.RepositoryAiContextSource
import app.nemla.core.data.ai.RoutedBudgetAdvisor
import app.nemla.core.data.ai.RoutedPeriodReviewer
import app.nemla.core.data.ai.RoutedRecurringChargeParser
import app.nemla.core.data.ai.RoutedSpeechTranscriber
import app.nemla.core.data.ai.RoutedStatementImporter
import app.nemla.core.data.ai.RoutedVoiceExpenseCapturer
import app.nemla.core.data.consent.SecureStoreAiConsentRepository
import app.nemla.core.data.preferences.SecureStorePreferencesRepository
import app.nemla.core.data.remote.KtorCategoryRepository
import app.nemla.core.data.remote.KtorDeferredDebitRepository
import app.nemla.core.data.remote.KtorExpenseRepository
import app.nemla.core.data.remote.KtorExpenseReviewRepository
import app.nemla.core.data.remote.KtorProfileRepository
import app.nemla.core.data.remote.KtorQuotaRepository
import app.nemla.core.data.remote.KtorRecurringChargeRepository
import app.nemla.core.data.remote.KtorReportRepository
import app.nemla.core.data.remote.Postgrest
import app.nemla.core.data.remote.UnsupportedRecurringChargeMaterializer
import app.nemla.core.data.session.GoTrueApi
import app.nemla.core.data.session.KtorAuthRepository
import app.nemla.core.data.session.SessionManager
import app.nemla.core.data.session.SessionStorage
import app.nemla.core.domain.port.AccountRepository
import app.nemla.core.domain.port.AiConsentRepository
import app.nemla.core.domain.port.AuthRepository
import app.nemla.core.domain.port.BudgetAdvisor
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.domain.port.DeferredDebitRepository
import app.nemla.core.domain.port.ExpenseRepository
import app.nemla.core.domain.port.ExpenseReviewRepository
import app.nemla.core.domain.port.PeriodReviewer
import app.nemla.core.domain.port.PreferencesRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.QuotaRepository
import app.nemla.core.domain.port.RecurringChargeMaterializer
import app.nemla.core.domain.port.RecurringChargeParser
import app.nemla.core.domain.port.RecurringChargeRepository
import app.nemla.core.domain.port.ReportRepository
import app.nemla.core.domain.port.SpeechTranscriber
import app.nemla.core.domain.port.StatementImporter
import app.nemla.core.domain.port.VoiceExpenseCapturer
import app.nemla.core.network.SessionTokenStore
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

val coreDataModule: Module =
    module {
        single { GoTrueApi(client = get(), config = get()) }
        single { SessionStorage(store = get(), logger = get()) }
        single { SecureStoreAiConsentRepository(store = get(), clock = Clock.System, logger = get()) } bind AiConsentRepository::class
        single {
            val consents = get<SecureStoreAiConsentRepository>()
            SessionManager(
                goTrue = get(),
                storage = get(),
                clock = Clock.System,
                logger = get(),
                onSessionEnded = { consents.clear() },
            )
        } bind SessionTokenStore::class
        single { Postgrest(client = get(), config = get(), sessions = get()) }

        single<AuthRepository> {
            KtorAuthRepository(
                goTrue = get(),
                sessions = get(),
                tokens = get(),
                clock = Clock.System,
                logger = get(),
            )
        }
        single<ProfileRepository> { KtorProfileRepository(postgrest = get(), goTrue = get(), logger = get()) }
        single<CategoryRepository> { KtorCategoryRepository(postgrest = get()) }
        single<QuotaRepository> { KtorQuotaRepository(postgrest = get()) }
        single<ExpenseReviewRepository> { KtorExpenseReviewRepository(postgrest = get()) }
        single<ExpenseRepository> { KtorExpenseRepository(postgrest = get()) }
        single<RecurringChargeRepository> { KtorRecurringChargeRepository(postgrest = get()) }
        single<RecurringChargeMaterializer> { UnsupportedRecurringChargeMaterializer }
        single<DeferredDebitRepository> { KtorDeferredDebitRepository(postgrest = get()) }
        single<ReportRepository> { KtorReportRepository(postgrest = get()) }
        single<AccountRepository> { UnsupportedAccountRepository }
        single<PreferencesRepository> { SecureStorePreferencesRepository(store = get(), logger = get()) }

        single<ConnectivityMonitor> { AssumeOnlineConnectivityMonitor }
        single<AiContextSource> { RepositoryAiContextSource(connectivity = get(), quotas = get(), consents = get()) }
        single<AiTraceListener> { LoggingAiTraceListener(logger = get()) }
        single<AiRoutingPolicy> { LocalFirstPolicy }
        single { AiRouter(engines = getAll<AiEngine<*, *>>(), contextSource = get(), policy = get(), listener = get()) }

        single<VoiceExpenseCapturer> { RoutedVoiceExpenseCapturer(router = get(), categories = get(), clock = Clock.System) }
        single<StatementImporter> {
            RoutedStatementImporter(router = get(), categories = get(), profiles = get())
        }
        single<RecurringChargeParser> { RoutedRecurringChargeParser(router = get()) }
        single<SpeechTranscriber> { RoutedSpeechTranscriber(router = get()) }
        single<BudgetAdvisor> { RoutedBudgetAdvisor(router = get()) }
        single<PeriodReviewer> { RoutedPeriodReviewer(router = get()) }
    }
