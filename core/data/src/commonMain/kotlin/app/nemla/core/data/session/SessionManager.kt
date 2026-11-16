package app.nemla.core.data.session

import app.nemla.core.common.AppError
import app.nemla.core.common.AppLogger
import app.nemla.core.common.AppResult
import app.nemla.core.common.getOrNull
import app.nemla.core.common.info
import app.nemla.core.data.DataLog
import app.nemla.core.domain.model.EmailAddress
import app.nemla.core.domain.model.Session
import app.nemla.core.domain.model.UserId
import app.nemla.core.network.AccessToken
import app.nemla.core.network.SessionTokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

internal sealed interface SessionState {
    data object Unknown : SessionState

    data object SignedOut : SessionState

    class SignedIn(
        val stored: StoredSession,
    ) : SessionState {
        override fun toString(): String = "SignedIn(***)"
    }
}

internal class SessionManager(
    private val goTrue: GoTrueApi,
    private val storage: SessionStorage,
    private val clock: Clock,
    private val logger: AppLogger,
    private val onSessionEnded: suspend () -> Unit,
) : SessionTokenStore {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Unknown)

    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    suspend fun restore(): SessionState {
        mutableState.value.takeUnless { it == SessionState.Unknown }?.let { return it }
        return mutex.withLock {
            val current = mutableState.value
            if (current != SessionState.Unknown) return@withLock current
            val restored =
                when (val loaded = storage.load()) {
                    is AppResult.Success -> loaded.value?.let(SessionState::SignedIn) ?: SessionState.SignedOut
                    is AppResult.Failure -> SessionState.SignedOut
                }
            if (restored is SessionState.SignedIn) logger.info(DataLog.SESSION_RESTORED)
            mutableState.value = restored
            restored
        }
    }

    suspend fun currentSession(): StoredSession? = (restore() as? SessionState.SignedIn)?.stored

    suspend fun userId(): String? = currentSession()?.userId

    suspend fun establish(session: StoredSession): AppResult<Session> =
        mutex.withLock {
            when (val saved = storage.save(session)) {
                is AppResult.Failure -> {
                    AppResult.Failure(AppError.Storage)
                }

                is AppResult.Success -> {
                    mutableState.value = SessionState.SignedIn(session)
                    logger.info(DataLog.SESSION_ESTABLISHED)
                    AppResult.Success(session.toDomain())
                }
            }
        }

    suspend fun end(): AppResult<Unit> {
        val cleared = mutex.withLock { storage.clear().also { mutableState.value = SessionState.SignedOut } }
        onSessionEnded()
        logger.info(DataLog.SESSION_ENDED)
        return cleared
    }

    override suspend fun currentAccessToken(): AccessToken? = currentSession()?.let { AccessToken(it.accessToken, it.expiresAt) }

    override suspend fun refreshAccessToken(): AppResult<AccessToken> {
        val session = currentSession() ?: return AppResult.Failure(AppError.Unauthorized)
        val refreshed =
            when (val result = goTrue.refresh(session.refreshToken)) {
                is AppResult.Success -> result.value.toStoredSession(clock, session.email)
                is AppResult.Failure -> return result
            } ?: return AppResult.Failure(AppError.Server(null))
        val stillCurrent = mutex.withLock { (mutableState.value as? SessionState.SignedIn)?.stored?.userId == session.userId }
        if (!stillCurrent) return AppResult.Failure(AppError.Unauthorized)
        return when (val saved = establish(refreshed)) {
            is AppResult.Success -> AppResult.Success(AccessToken(refreshed.accessToken, refreshed.expiresAt))
            is AppResult.Failure -> saved
        }
    }

    override suspend fun invalidate() {
        end()
    }

    override fun toString(): String = "SessionManager(***)"
}

internal fun StoredSession.toDomain(): Session =
    Session(
        userId = UserId(userId),
        email = email?.let { EmailAddress.parse(it).getOrNull() },
        expiresAt = expiresAt,
    )
