package app.nemla.feature.auth

import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.EmailAddress
import app.nemla.core.domain.model.Password
import app.nemla.core.domain.model.Session
import app.nemla.core.domain.model.SignUpOutcome
import app.nemla.core.domain.model.SignUpRequest
import app.nemla.core.domain.model.UserId
import app.nemla.core.domain.port.AuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

internal val testSession = Session(UserId("user-1"), email = null, expiresAt = null)

internal const val VALID_EMAIL = "lea@example.com"
internal const val VALID_PASSWORD = "secret-123"

internal class FakeAuthRepository : AuthRepository {
    val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    var signInResult: AppResult<Session> = AppResult.Success(testSession)
    var signUpResult: AppResult<SignUpOutcome> = AppResult.Success(SignUpOutcome.ConfirmationRequired)
    var resetResult: AppResult<Unit> = AppResult.Success(Unit)
    var gate: CompletableDeferred<Unit>? = null

    val signInCalls = mutableListOf<Pair<EmailAddress, Password>>()
    val signUpCalls = mutableListOf<SignUpRequest>()
    val resetCalls = mutableListOf<EmailAddress>()

    override fun observeAuthState(): Flow<AuthState> = state

    override suspend fun signIn(
        email: EmailAddress,
        password: Password,
    ): AppResult<Session> {
        signInCalls += email to password
        gate?.await()
        return signInResult
    }

    override suspend fun signUp(request: SignUpRequest): AppResult<SignUpOutcome> {
        signUpCalls += request
        gate?.await()
        return signUpResult
    }

    override suspend fun signOut(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun requestPasswordReset(email: EmailAddress): AppResult<Unit> {
        resetCalls += email
        gate?.await()
        return resetResult
    }

    override suspend fun refreshSession(): AppResult<Session> = AppResult.Success(testSession)
}

@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest {
    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }
}
