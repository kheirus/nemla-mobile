package app.nemla.core.domain

import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AiConsents
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.EmailAddress
import app.nemla.core.domain.model.Password
import app.nemla.core.domain.model.Session
import app.nemla.core.domain.model.SignUpOutcome
import app.nemla.core.domain.model.SignUpRequest
import app.nemla.core.domain.model.UserId
import app.nemla.core.domain.port.AiConsentRepository
import app.nemla.core.domain.port.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Instant

internal val sampleSession = Session(UserId("user-1"), email = null, expiresAt = Instant.fromEpochSeconds(1_800_000_000))

internal class FakeAuthRepository : AuthRepository {
    val state = MutableStateFlow<AuthState>(AuthState.Unknown)
    var signInResult: AppResult<Session> = AppResult.Success(sampleSession)
    var signUpResult: AppResult<SignUpOutcome> = AppResult.Success(SignUpOutcome.ConfirmationRequired)
    var signOutResult: AppResult<Unit> = AppResult.Success(Unit)
    var resetResult: AppResult<Unit> = AppResult.Success(Unit)

    val signInCalls = mutableListOf<Pair<EmailAddress, Password>>()
    val signUpCalls = mutableListOf<SignUpRequest>()
    val resetCalls = mutableListOf<EmailAddress>()
    var signOutCalls = 0

    override fun observeAuthState(): Flow<AuthState> = state

    override suspend fun signIn(
        email: EmailAddress,
        password: Password,
    ): AppResult<Session> {
        signInCalls += email to password
        return signInResult
    }

    override suspend fun signUp(request: SignUpRequest): AppResult<SignUpOutcome> {
        signUpCalls += request
        return signUpResult
    }

    override suspend fun signOut(): AppResult<Unit> {
        signOutCalls++
        return signOutResult
    }

    override suspend fun requestPasswordReset(email: EmailAddress): AppResult<Unit> {
        resetCalls += email
        return resetResult
    }

    override suspend fun refreshSession(): AppResult<Session> = AppResult.Success(sampleSession)
}

internal class FakeAiConsentRepository(
    private val now: Instant = Instant.fromEpochSeconds(1_750_000_000),
) : AiConsentRepository {
    val state = MutableStateFlow(AiConsents.None)
    var failure: AppResult.Failure? = null
    val revoked = mutableListOf<AiConsentLevel>()

    override fun observe(): Flow<AiConsents> = state

    override suspend fun current(): AppResult<AiConsents> = AppResult.Success(state.value)

    override suspend fun grant(level: AiConsentLevel): AppResult<Unit> {
        failure?.let { return it }
        state.value = AiConsents(state.value.grants + (level to now))
        return AppResult.Success(Unit)
    }

    override suspend fun revoke(level: AiConsentLevel): AppResult<Unit> {
        failure?.let { return it }
        revoked += level
        state.value = AiConsents(state.value.grants - level)
        return AppResult.Success(Unit)
    }
}
