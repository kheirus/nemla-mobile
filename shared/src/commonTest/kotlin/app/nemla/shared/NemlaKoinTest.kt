package app.nemla.shared

import app.nemla.ai.api.AiCapability
import app.nemla.ai.api.AiRouter
import app.nemla.core.audio.MicrophoneAccess
import app.nemla.core.audio.MicrophonePermission
import app.nemla.core.audio.RecorderState
import app.nemla.core.audio.VoiceRecorder
import app.nemla.core.audio.VoiceRecorderFactory
import app.nemla.core.common.AppBuildInfo
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.EmailAddress
import app.nemla.core.domain.model.Password
import app.nemla.core.domain.model.Session
import app.nemla.core.domain.model.SignUpOutcome
import app.nemla.core.domain.model.SignUpRequest
import app.nemla.core.domain.model.UserId
import app.nemla.core.domain.port.AccountRepository
import app.nemla.core.domain.port.AiConsentRepository
import app.nemla.core.domain.port.AuthRepository
import app.nemla.core.domain.port.DeferredDebitRepository
import app.nemla.core.domain.port.ExpenseRepository
import app.nemla.core.domain.port.ExpenseReviewRepository
import app.nemla.core.domain.port.PendingReviewOpener
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.RecurringChargeMaterializer
import app.nemla.core.domain.port.RecurringChargeRepository
import app.nemla.core.domain.port.VoiceExpenseCapturer
import app.nemla.core.domain.usecase.BootstrapAndLoadProfile
import app.nemla.core.domain.usecase.CaptureVoiceExpenses
import app.nemla.core.domain.usecase.ConfirmReviewedExpenses
import app.nemla.core.domain.usecase.DeleteExpense
import app.nemla.core.domain.usecase.DeletePendingExpense
import app.nemla.core.domain.usecase.FindLearnedExpenses
import app.nemla.core.domain.usecase.GetDeferredDebit
import app.nemla.core.domain.usecase.GetMonthlyQuota
import app.nemla.core.domain.usecase.GrantAiConsent
import app.nemla.core.domain.usecase.ListCategories
import app.nemla.core.domain.usecase.ListExpenses
import app.nemla.core.domain.usecase.ListRecurringCharges
import app.nemla.core.domain.usecase.MaterializeRecurringCharges
import app.nemla.core.domain.usecase.ObserveAiConsents
import app.nemla.core.domain.usecase.ObserveAuthState
import app.nemla.core.domain.usecase.RequestPasswordReset
import app.nemla.core.domain.usecase.SearchExpensesByDate
import app.nemla.core.domain.usecase.SignIn
import app.nemla.core.domain.usecase.SignOut
import app.nemla.core.domain.usecase.SignUp
import app.nemla.core.domain.usecase.UpdateExpense
import app.nemla.core.network.BackendConfig
import app.nemla.core.security.InMemorySecureStore
import app.nemla.core.security.ScreenProtection
import app.nemla.core.security.SecureStore
import app.nemla.feature.auth.navigation.AuthEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NemlaKoinTest {
    private val config =
        BackendConfig(
            supabaseUrl = "https://project.supabase.co",
            supabasePublishableKey = "sb_publishable_test",
            apiBaseUrl = "https://nemla.app",
        )

    private val testOverrides =
        module {
            single<SecureStore> { InMemorySecureStore() }
            single<MicrophonePermission> { GrantedMicrophone }
            single<VoiceRecorderFactory> { VoiceRecorderFactory { IdleRecorder } }
        }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun withGraph(
        backend: BackendConfig = config,
        isDebug: Boolean = false,
        block: (Koin) -> Unit,
    ) {
        val application = koinApplication { modules(nemlaModules(backend, AppBuildInfo(isDebug), module { }) + testOverrides) }
        try {
            block(application.koin)
        } finally {
            application.close()
        }
    }

    @Test
    fun wholeGraphResolves() =
        withGraph { koin ->
            assertTrue(koin.get<BackendConfig>().isComplete)
            koin.get<AuthRepository>()
            koin.get<ProfileRepository>()
            koin.get<AiConsentRepository>()
            koin.get<AccountRepository>()
            koin.get<VoiceExpenseCapturer>()
            koin.get<ExpenseReviewRepository>()
            koin.get<CaptureVoiceExpenses>()
            koin.get<GetMonthlyQuota>()
            koin.get<ListCategories>()
            koin.get<FindLearnedExpenses>()
            koin.get<ConfirmReviewedExpenses>()
            koin.get<DeletePendingExpense>()
            koin.get<ObserveAiConsents>()
            koin.get<GrantAiConsent>()
            koin.get<ObserveAuthState>()
            koin.get<SignIn>()
            koin.get<SignUp>()
            koin.get<SignOut>()
            koin.get<RequestPasswordReset>()
            koin.get<AppViewModel>()
            koin.get<ExpenseRepository>()
            koin.get<RecurringChargeRepository>()
            koin.get<RecurringChargeMaterializer>()
            koin.get<DeferredDebitRepository>()
            koin.get<PendingReviewOpener>()
            koin.get<BootstrapAndLoadProfile>()
            koin.get<ListExpenses>()
            koin.get<SearchExpensesByDate>()
            koin.get<ListRecurringCharges>()
            koin.get<MaterializeRecurringCharges>()
            koin.get<GetDeferredDebit>()
            koin.get<UpdateExpense>()
            koin.get<DeleteExpense>()
            assertTrue(koin.get<ScreenProtection>().isEnabled)
            val router = koin.get<AiRouter>()
            assertEquals(1, router.enginesFor(AiCapability.StatementAnonymization).size)
            assertEquals(1, router.enginesFor(AiCapability.VoiceExpenseCapture).size)
        }

    @Test
    fun debugBuildsDisableScreenProtection() =
        withGraph(isDebug = true) { koin ->
            assertFalse(koin.get<ScreenProtection>().isEnabled)
        }

    @Test
    fun incompleteConfigurationStillBuildsTheGraph() =
        withGraph(backend = BackendConfig("", "", ""), isDebug = true) { koin ->
            assertFalse(koin.get<BackendConfig>().isComplete)
            koin.get<AppViewModel>()
        }

    @Test
    fun appViewModelFollowsTheSession() {
        val auth = StateAuthRepository()
        val vm = AppViewModel(ObserveAuthState(auth))
        assertEquals(AuthState.Unknown, vm.authState.value)
        auth.state.value = AuthState.SignedIn(Session(UserId("u"), email = null, expiresAt = null))
        assertTrue(vm.authState.value is AuthState.SignedIn)
        auth.state.value = AuthState.SignedOut
        assertEquals(AuthState.SignedOut, vm.authState.value)
    }

    @Test
    fun rootTargetStartsWithSignUpAndReturnsToSignInAfterASession() {
        assertNull(rootTargetFor(AuthState.Unknown, hadSession = false))
        assertEquals(RootTarget.Auth(AuthEntry.SignUp), rootTargetFor(AuthState.SignedOut, hadSession = false))
        assertEquals(RootTarget.Auth(AuthEntry.SignIn), rootTargetFor(AuthState.SignedOut, hadSession = true))
        val signedIn = AuthState.SignedIn(Session(UserId("u"), email = null, expiresAt = null))
        assertEquals(RootTarget.Home, rootTargetFor(signedIn, hadSession = false))
    }
}

private object GrantedMicrophone : MicrophonePermission {
    override fun status(): MicrophoneAccess = MicrophoneAccess.GRANTED
}

private object IdleRecorder : VoiceRecorder {
    override val state: StateFlow<RecorderState> = MutableStateFlow(RecorderState.Idle)

    override fun start() = Unit

    override fun stop() = Unit

    override fun cancel() = Unit
}

private class StateAuthRepository : AuthRepository {
    val state = MutableStateFlow<AuthState>(AuthState.Unknown)
    private val session = Session(UserId("u"), email = null, expiresAt = null)

    override fun observeAuthState(): Flow<AuthState> = state

    override suspend fun signIn(
        email: EmailAddress,
        password: Password,
    ): AppResult<Session> = AppResult.Success(session)

    override suspend fun signUp(request: SignUpRequest): AppResult<SignUpOutcome> = AppResult.Success(SignUpOutcome.ConfirmationRequired)

    override suspend fun signOut(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun requestPasswordReset(email: EmailAddress): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun refreshSession(): AppResult<Session> = AppResult.Success(session)
}
