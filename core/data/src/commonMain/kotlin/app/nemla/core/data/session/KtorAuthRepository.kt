package app.nemla.core.data.session

import app.nemla.core.common.AppError
import app.nemla.core.common.AppLogger
import app.nemla.core.common.AppResult
import app.nemla.core.common.flatMap
import app.nemla.core.common.map
import app.nemla.core.common.warn
import app.nemla.core.data.DataLog
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.EmailAddress
import app.nemla.core.domain.model.Gender
import app.nemla.core.domain.model.Household
import app.nemla.core.domain.model.Password
import app.nemla.core.domain.model.Session
import app.nemla.core.domain.model.SignUpDetails
import app.nemla.core.domain.model.SignUpOutcome
import app.nemla.core.domain.model.SignUpRequest
import app.nemla.core.domain.port.AuthRepository
import app.nemla.core.network.TokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

internal class KtorAuthRepository(
    private val goTrue: GoTrueApi,
    private val sessions: SessionManager,
    private val tokens: TokenProvider,
    private val clock: Clock,
    private val logger: AppLogger,
) : AuthRepository {
    override fun observeAuthState(): Flow<AuthState> =
        flow {
            sessions.restore()
            emitAll(sessions.state.map { it.toAuthState() })
        }.distinctUntilChanged()

    override suspend fun signIn(
        email: EmailAddress,
        password: Password,
    ): AppResult<Session> =
        goTrue.signInWithPassword(email.value, password.value).flatMap { dto ->
            val stored = dto.toStoredSession(clock, email.value) ?: return@flatMap AppResult.Failure(AppError.Server(null))
            sessions.establish(stored)
        }

    override suspend fun signUp(request: SignUpRequest): AppResult<SignUpOutcome> {
        val body = SignUpDto(request.email.value, request.password.value, request.details.toMetadata())
        return goTrue.signUp(body).flatMap { dto ->
            val stored = dto.toStoredSession(clock, request.email.value)
            if (stored == null) {
                AppResult.Success(SignUpOutcome.ConfirmationRequired)
            } else {
                sessions.establish(stored).map { SignUpOutcome.SignedIn(it) }
            }
        }
    }

    override suspend fun signOut(): AppResult<Unit> {
        if (sessions.currentSession() != null) {
            val remote = goTrue.signOut()
            if (remote is AppResult.Failure) logger.warn(DataLog.REMOTE_SIGN_OUT_FAILED)
        }
        return sessions.end()
    }

    override suspend fun requestPasswordReset(email: EmailAddress): AppResult<Unit> = goTrue.recover(email.value)

    override suspend fun refreshSession(): AppResult<Session> {
        val refreshed = tokens.refresh(rejected = null)
        if (refreshed is AppResult.Failure) return refreshed
        val session = sessions.currentSession() ?: return AppResult.Failure(AppError.Unauthorized)
        return AppResult.Success(session.toDomain())
    }
}

internal fun SessionState.toAuthState(): AuthState =
    when (this) {
        SessionState.Unknown -> AuthState.Unknown
        SessionState.SignedOut -> AuthState.SignedOut
        is SessionState.SignedIn -> AuthState.SignedIn(stored.toDomain())
    }

internal fun SignUpDetails.toMetadata(): SignUpMetadataDto =
    SignUpMetadataDto(
        pseudo = pseudo,
        age = age,
        sexe = gender?.toBackend(),
        situationFamiliale = household?.toBackend(),
    )

internal fun Gender.toBackend(): String =
    when (this) {
        Gender.FEMALE -> "femme"
        Gender.MALE -> "homme"
    }

internal fun Household.toBackend(): String =
    when (this) {
        Household.SINGLE -> "celibataire"
        Household.COUPLE -> "couple"
        Household.FAMILY -> "famille"
        Household.FLATSHARE -> "colocation"
    }

internal fun genderFromBackend(value: String?): Gender? = Gender.entries.firstOrNull { it.toBackend() == value }

internal fun householdFromBackend(value: String?): Household? = Household.entries.firstOrNull { it.toBackend() == value }
