package app.nemla.core.data.session

import app.nemla.core.common.AppError
import app.nemla.core.common.AppLogger
import app.nemla.core.common.AppResult
import app.nemla.core.common.warn
import app.nemla.core.data.DataLog
import app.nemla.core.security.SecureStore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Instant

@Serializable
internal class StoredSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAtEpochSeconds: Long?,
    @SerialName("user_id") val userId: String,
    @SerialName("email") val email: String?,
) {
    val expiresAt: Instant? get() = expiresAtEpochSeconds?.let(Instant::fromEpochSeconds)

    override fun toString(): String = "StoredSession(***, expiresAt=$expiresAtEpochSeconds)"
}

internal class SessionStorage(
    private val store: SecureStore,
    private val logger: AppLogger,
) {
    suspend fun load(): AppResult<StoredSession?> {
        val raw =
            when (val result = store.get(KEY)) {
                is AppResult.Success -> {
                    result.value ?: return AppResult.Success(null)
                }

                is AppResult.Failure -> {
                    logger.warn(DataLog.SESSION_READ_FAILED)
                    return result
                }
            }
        val decoded =
            try {
                json.decodeFromString(StoredSession.serializer(), raw)
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        if (decoded == null) {
            logger.warn(DataLog.SESSION_DISCARDED)
            store.remove(KEY)
        }
        return AppResult.Success(decoded)
    }

    suspend fun save(session: StoredSession): AppResult<Unit> {
        val result = store.put(KEY, json.encodeToString(StoredSession.serializer(), session))
        if (result is AppResult.Failure) logger.warn(DataLog.SESSION_WRITE_FAILED)
        return result
    }

    suspend fun clear(): AppResult<Unit> =
        when (val result = store.remove(KEY)) {
            is AppResult.Success -> result
            is AppResult.Failure -> AppResult.Failure(AppError.Storage)
        }

    companion object {
        const val KEY: String = "auth.session.v1"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
