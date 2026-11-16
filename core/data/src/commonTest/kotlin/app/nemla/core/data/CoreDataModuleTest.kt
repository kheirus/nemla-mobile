package app.nemla.core.data

import app.nemla.ai.api.AiCapability
import app.nemla.ai.api.AiRouter
import app.nemla.ai.local.aiLocalModule
import app.nemla.core.common.AppBuildInfo
import app.nemla.core.common.AppLogger
import app.nemla.core.common.NoOpLogger
import app.nemla.core.data.ai.ConnectivityMonitor
import app.nemla.core.data.session.SessionManager
import app.nemla.core.domain.port.AccountRepository
import app.nemla.core.domain.port.AiConsentRepository
import app.nemla.core.domain.port.AuthRepository
import app.nemla.core.domain.port.BudgetAdvisor
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.domain.port.DeferredDebitRepository
import app.nemla.core.domain.port.ExpenseRepository
import app.nemla.core.domain.port.ExpenseReviewRepository
import app.nemla.core.domain.port.PeriodReviewer
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.QuotaRepository
import app.nemla.core.domain.port.RecurringChargeMaterializer
import app.nemla.core.domain.port.RecurringChargeParser
import app.nemla.core.domain.port.RecurringChargeRepository
import app.nemla.core.domain.port.StatementImporter
import app.nemla.core.domain.port.VoiceExpenseCapturer
import app.nemla.core.network.SessionTokenStore
import app.nemla.core.network.coreNetworkModule
import app.nemla.core.security.InMemorySecureStore
import app.nemla.core.security.SecureStore
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CoreDataModuleTest {
    private val externals =
        module {
            single { testConfig }
            single<AppLogger> { NoOpLogger }
            single { AppBuildInfo(isDebug = false) }
            single<SecureStore> { InMemorySecureStore() }
        }

    @Test
    fun everyDomainPortIsBound() =
        runTest {
            val koin = koinApplication { modules(externals, coreNetworkModule, aiLocalModule, coreDataModule) }.koin
            koin.get<AuthRepository>()
            koin.get<ProfileRepository>()
            koin.get<CategoryRepository>()
            koin.get<QuotaRepository>()
            koin.get<ExpenseReviewRepository>()
            koin.get<ExpenseRepository>()
            koin.get<RecurringChargeRepository>()
            koin.get<RecurringChargeMaterializer>()
            koin.get<DeferredDebitRepository>()
            koin.get<AiConsentRepository>()
            koin.get<AccountRepository>()
            koin.get<VoiceExpenseCapturer>()
            koin.get<StatementImporter>()
            koin.get<RecurringChargeParser>()
            koin.get<BudgetAdvisor>()
            koin.get<PeriodReviewer>()
            koin.get<ConnectivityMonitor>()
            assertIs<SessionManager>(koin.get<SessionTokenStore>())
            assertSame(koin.get<SessionTokenStore>(), koin.get<SessionManager>())
            val router = koin.get<AiRouter>()
            assertEquals(1, router.enginesFor(AiCapability.StatementAnonymization).size)
            assertTrue(router.enginesFor(AiCapability.StatementParsing).isEmpty())
            assertTrue(koin.get<ConnectivityMonitor>().isOnline())
        }

    @Test
    fun connectivityMonitorCanBeReplaced() =
        runTest {
            val offline =
                object : ConnectivityMonitor {
                    override suspend fun isOnline(): Boolean = false
                }
            val override = module { single<ConnectivityMonitor> { offline } }
            val koin = koinApplication { modules(externals, coreNetworkModule, coreDataModule, override) }.koin
            assertSame(offline, koin.get<ConnectivityMonitor>())
        }
}
