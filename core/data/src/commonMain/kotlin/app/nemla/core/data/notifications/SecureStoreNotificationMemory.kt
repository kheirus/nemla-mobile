package app.nemla.core.data.notifications

import app.nemla.core.common.AppError
import app.nemla.core.common.AppLogger
import app.nemla.core.common.AppResult
import app.nemla.core.common.warn
import app.nemla.core.data.DataLog
import app.nemla.core.domain.port.NotificationMemory
import app.nemla.core.security.SecureStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minusMonth
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearMonth
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Clock

@Serializable
internal class NotificationMemoryDto(
    val alerted: List<String> = emptyList(),
)

internal class SecureStoreNotificationMemory(
    private val store: SecureStore,
    private val clock: Clock,
    private val logger: AppLogger,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : NotificationMemory {
    private val mutex = Mutex()

    override suspend fun wasAlerted(key: String): AppResult<Boolean> =
        mutex.withLock { read() }.let { result ->
            when (result) {
                is AppResult.Success -> AppResult.Success(key in result.value)
                is AppResult.Failure -> result
            }
        }

    override suspend fun remember(key: String): AppResult<Unit> =
        mutex.withLock {
            val current =
                when (val result = read()) {
                    is AppResult.Success -> result.value
                    is AppResult.Failure -> emptySet()
                }
            val kept = (current + key).filter(::isRecent).sorted()
            when (store.put(KEY, json.encodeToString(NotificationMemoryDto.serializer(), NotificationMemoryDto(kept)))) {
                is AppResult.Success -> AppResult.Success(Unit)
                is AppResult.Failure -> AppResult.Failure(AppError.Storage)
            }
        }

    override suspend fun clear(): AppResult<Unit> =
        mutex.withLock {
            when (store.remove(KEY)) {
                is AppResult.Success -> AppResult.Success(Unit)
                is AppResult.Failure -> AppResult.Failure(AppError.Storage)
            }
        }

    private fun isRecent(key: String): Boolean {
        val month = clock.todayIn(timeZone()).yearMonth
        val prefix = key.substringBefore(':')
        return prefix == month.toString() || prefix == month.minusMonth().toString()
    }

    private suspend fun read(): AppResult<Set<String>> {
        val raw =
            when (val result = store.get(KEY)) {
                is AppResult.Success -> {
                    result.value ?: return AppResult.Success(emptySet())
                }

                is AppResult.Failure -> {
                    logger.warn(DataLog.NOTIFICATION_MEMORY_READ_FAILED)
                    return AppResult.Failure(AppError.Storage)
                }
            }
        return try {
            AppResult.Success(json.decodeFromString(NotificationMemoryDto.serializer(), raw).alerted.toSet())
        } catch (e: SerializationException) {
            logger.warn(DataLog.NOTIFICATION_MEMORY_READ_FAILED)
            AppResult.Success(emptySet())
        } catch (e: IllegalArgumentException) {
            logger.warn(DataLog.NOTIFICATION_MEMORY_READ_FAILED)
            AppResult.Success(emptySet())
        }
    }

    companion object {
        const val KEY: String = "notifications.v1"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
