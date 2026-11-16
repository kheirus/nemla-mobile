package app.nemla.shared

import app.nemla.ai.api.AiCapability
import app.nemla.ai.api.AiRouter
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
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.VoiceExpenseCapturer
import app.nemla.core.domain.usecase.ObserveAuthState
import app.nemla.core.domain.usecase.RequestPasswordReset
import app.nemla.core.domain.usecase.SignIn
import app.nemla.core.domain.usecase.SignOut
import app.nemla.core.domain.usecase.SignUp
import app.nemla.core.network.BackendConfig
import app.nemla.core.security.InMemorySecureStore
import app.nemla.core.security.ScreenProtection
import app.nemla.core.security.SecureStore
import app.nemla.feature.auth.navigation.AuthEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val testOverrides = module { single<SecureStore> { InMemorySecureStore() } }

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
            koin.get<ObserveAuthState>()
            koin.get<SignIn>()
            koin.get<SignUp>()
            koin.get<SignOut>()
            koin.get<RequestPasswordReset>()
            koin.get<AppViewModel>()
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
