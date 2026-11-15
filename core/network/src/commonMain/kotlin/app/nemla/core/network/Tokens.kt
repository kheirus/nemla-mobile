package app.nemla.core.network

import app.nemla.core.common.AppError
import app.nemla.core.common.AppLogger
import app.nemla.core.common.AppResult
import app.nemla.core.common.NoOpLogger
import app.nemla.core.common.info
import app.nemla.core.common.warn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AccessToken(
    val value: String,
    val expiresAt: Instant?,
) {
    fun isExpiringAt(
        now: Instant,
        leeway: Duration,
    ): Boolean = expiresAt != null && expiresAt - leeway <= now

    override fun equals(other: Any?): Boolean = other is AccessToken && other.value == value && other.expiresAt == expiresAt

    override fun hashCode(): Int = 31 * value.hashCode() + (expiresAt?.hashCode() ?: 0)

    override fun toString(): String = "AccessToken(***, expiresAt=$expiresAt)"
}

interface SessionTokenStore {
    suspend fun currentAccessToken(): AccessToken?

    suspend fun refreshAccessToken(): AppResult<AccessToken>

    suspend fun invalidate()
}

interface TokenProvider {
    suspend fun accessToken(): AccessToken?

    suspend fun refresh(rejected: AccessToken?): AppResult<AccessToken>

    suspend fun invalidate()
}

fun createTokenProvider(
    store: Lazy<SessionTokenStore>,
    clock: Clock = Clock.System,
    logger: AppLogger = NoOpLogger,
    refreshLeeway: Duration = 30.seconds,
): TokenProvider = MutexTokenProvider(store, clock, logger, refreshLeeway)

internal class MutexTokenProvider(
    private val store: Lazy<SessionTokenStore>,
    private val clock: Clock,
    private val logger: AppLogger,
    private val refreshLeeway: Duration,
) : TokenProvider {
    private val mutex = Mutex()

    override suspend fun accessToken(): AccessToken? {
        val current = store.value.currentAccessToken() ?: return null
        if (!current.isExpiringAt(clock.now(), refreshLeeway)) return current
        return when (val refreshed = refresh(current)) {
            is AppResult.Success -> refreshed.value
            is AppResult.Failure -> if (refreshed.error == AppError.Unauthorized) null else current
        }
    }

    override suspend fun refresh(rejected: AccessToken?): AppResult<AccessToken> =
        mutex.withLock {
            val tokens = store.value
            val current = tokens.currentAccessToken() ?: return@withLock AppResult.Failure(AppError.Unauthorized)
            val alreadyRefreshed =
                rejected != null && current.value != rejected.value && !current.isExpiringAt(clock.now(), refreshLeeway)
            if (alreadyRefreshed) return@withLock AppResult.Success(current)
            when (val result = tokens.refreshAccessToken()) {
                is AppResult.Success -> {
                    logger.info(NetworkLog.SESSION_REFRESHED)
                    result
                }

                is AppResult.Failure -> {
                    logger.warn(NetworkLog.SESSION_REFRESH_FAILED)
                    if (result.error == AppError.Unauthorized) {
                        tokens.invalidate()
                        logger.info(NetworkLog.SESSION_INVALIDATED)
                    }
                    result
                }
            }
        }

    override suspend fun invalidate() {
        mutex.withLock { store.value.invalidate() }
    }
}
