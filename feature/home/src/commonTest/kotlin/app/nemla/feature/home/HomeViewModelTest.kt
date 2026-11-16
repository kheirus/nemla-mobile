package app.nemla.feature.home

import app.cash.turbine.test
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.EmailAddress
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.Password
import app.nemla.core.domain.model.ProfileIdentity
import app.nemla.core.domain.model.PseudoAvailability
import app.nemla.core.domain.model.Session
import app.nemla.core.domain.model.SignUpOutcome
import app.nemla.core.domain.model.SignUpRequest
import app.nemla.core.domain.model.UserId
import app.nemla.core.domain.model.UserProfile
import app.nemla.core.domain.port.AuthRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.usecase.SignOut
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
class HomeViewModelTest {
    private val profiles = FakeProfileRepository()
    private val auth = FakeAuthRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = HomeViewModel(LoadGreetingName(profiles), SignOut(auth))

    @Test
    fun greetingUsesTheTrimmedPseudoAfterBootstrap() {
        profiles.pseudo = "  Léa "
        val vm = viewModel()
        assertEquals("Léa", vm.state.value.pseudo)
        assertEquals(1, profiles.bootstrapCalls)
    }

    @Test
    fun greetingFallsBackWhenTheProfileIsUnavailable() {
        profiles.profileResult = AppResult.Failure(AppError.Network.Offline)
        assertNull(viewModel().state.value.pseudo)
    }

    @Test
    fun blankPseudoIsIgnored() {
        profiles.pseudo = "   "
        assertNull(viewModel().state.value.pseudo)
    }

    @Test
    fun signOutEmitsSignedOut() =
        runTest {
            val vm = viewModel()
            vm.effects.test {
                vm.onAction(HomeAction.SignOutClicked)
                assertEquals(HomeEffect.SignedOut, awaitItem())
            }
            assertEquals(1, auth.signOutCalls)
            assertFalse(vm.state.value.isSigningOut)
        }

    @Test
    fun signOutFailureIsShownAndRetryable() {
        auth.signOutResult = AppResult.Failure(AppError.Storage)
        val vm = viewModel()
        vm.onAction(HomeAction.SignOutClicked)
        assertTrue(vm.state.value.signOutFailed)
        auth.signOutResult = AppResult.Success(Unit)
        vm.onAction(HomeAction.SignOutClicked)
        assertFalse(vm.state.value.signOutFailed)
        assertEquals(2, auth.signOutCalls)
    }

    @Test
    fun repeatedSignOutIsIgnoredWhileRunning() {
        auth.gate = CompletableDeferred()
        val vm = viewModel()
        vm.onAction(HomeAction.SignOutClicked)
        vm.onAction(HomeAction.SignOutClicked)
        assertTrue(vm.state.value.isSigningOut)
        assertEquals(1, auth.signOutCalls)
    }

    @Test
    fun stateDoesNotPrintThePseudo() {
        profiles.pseudo = "Léa"
        assertFalse(
            viewModel()
                .state.value
                .toString()
                .contains("Léa"),
        )
    }

    @Test
    fun moduleResolvesTheViewModel() {
        val koin =
            koinApplication {
                modules(
                    module {
                        single<AuthRepository> { auth }
                        single<ProfileRepository> { profiles }
                    },
                    featureHomeModule,
                )
            }.koin
        koin.get<HomeViewModel>()
    }
}

private class FakeProfileRepository : ProfileRepository {
    var pseudo: String? = null
    var profileResult: AppResult<UserProfile>? = null
    var bootstrapCalls = 0

    override suspend fun bootstrap(): AppResult<Unit> {
        bootstrapCalls++
        return AppResult.Success(Unit)
    }

    override suspend fun getProfile(): AppResult<UserProfile> =
        profileResult ?: AppResult.Success(
            UserProfile(
                userId = UserId("user-1"),
                pseudo = pseudo,
                age = null,
                gender = null,
                household = null,
                currency = Currency.fromCode(null),
                budget = GlobalBudget(monthlyCents = 0, annualCents = null, period = BudgetPeriod.MONTH),
                onboardingDone = false,
                dailyReminder = true,
            ),
        )

    override suspend fun updateIdentity(identity: ProfileIdentity): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun updateBudget(budget: GlobalBudget): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun setDailyReminder(enabled: Boolean): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun checkPseudo(pseudo: String): AppResult<PseudoAvailability> =
        AppResult.Success(PseudoAvailability(available = true, suggestions = emptyList()))
}

private class FakeAuthRepository : AuthRepository {
    var signOutResult: AppResult<Unit> = AppResult.Success(Unit)
    var signOutCalls = 0
    var gate: CompletableDeferred<Unit>? = null
    private val session = Session(UserId("user-1"), email = null, expiresAt = null)

    override fun observeAuthState(): Flow<AuthState> = flowOf(AuthState.SignedIn(session))

    override suspend fun signIn(
        email: EmailAddress,
        password: Password,
    ): AppResult<Session> = AppResult.Success(session)

    override suspend fun signUp(request: SignUpRequest): AppResult<SignUpOutcome> = AppResult.Success(SignUpOutcome.ConfirmationRequired)

    override suspend fun signOut(): AppResult<Unit> {
        signOutCalls++
        gate?.await()
        return signOutResult
    }

    override suspend fun requestPasswordReset(email: EmailAddress): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun refreshSession(): AppResult<Session> = AppResult.Success(session)
}
