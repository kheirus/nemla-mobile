package app.nemla.core.data.consent

import app.nemla.core.common.AppError
import app.nemla.core.common.AppLogger
import app.nemla.core.common.AppResult
import app.nemla.core.common.warn
import app.nemla.core.data.DataLog
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AiConsents
import app.nemla.core.domain.port.AiConsentRepository
import app.nemla.core.security.SecureStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

internal class SecureStoreAiConsentRepository(
    private val store: SecureStore,
    private val clock: Clock,
    private val logger: AppLogger,
) : AiConsentRepository {
    private val mutex = Mutex()
    private val state = MutableStateFlow<AiConsents?>(null)

    override fun observe(): Flow<AiConsents> =
        flow {
            load()
            emitAll(state.filterNotNull())
        }

    override suspend fun current(): AppResult<AiConsents> = AppResult.Success(load())

    override suspend fun grant(level: AiConsentLevel): AppResult<Unit> =
        update { grants -> if (level in grants) grants else grants + (level to clock.now()) }

    override suspend fun revoke(level: AiConsentLevel): AppResult<Unit> = update { grants -> grants - level }

    suspend fun clear(): AppResult<Unit> =
        mutex.withLock {
            val removed = store.remove(KEY)
            state.value = AiConsents.None
            if (removed is AppResult.Failure) AppResult.Failure(AppError.Storage) else AppResult.Success(Unit)
        }

    private suspend fun load(): AiConsents {
        state.value?.let { return it }
        return mutex.withLock {
            state.value ?: read().also { state.value = it }
        }
    }

    private suspend fun read(): AiConsents {
        val raw =
            when (val result = store.get(KEY)) {
                is AppResult.Success -> {
                    result.value ?: return AiConsents.None
                }

                is AppResult.Failure -> {
                    logger.warn(DataLog.CONSENT_READ_FAILED)
                    return AiConsents.None
                }
            }
        val decoded =
            try {
                json.decodeFromString(serializer, raw)
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        if (decoded == null) {
            logger.warn(DataLog.CONSENT_READ_FAILED)
            return AiConsents.None
        }
        val grants =
            decoded.mapNotNull { (name, epochMillis) ->
                AiConsentLevel.entries.firstOrNull { it.name == name }?.let { it to Instant.fromEpochMilliseconds(epochMillis) }
            }
        return AiConsents(grants.toMap())
    }

    private suspend fun update(transform: (Map<AiConsentLevel, Instant>) -> Map<AiConsentLevel, Instant>): AppResult<Unit> {
        load()
        return mutex.withLock {
            val current = state.value ?: AiConsents.None
            val next = AiConsents(transform(current.grants))
            if (next == current) return@withLock AppResult.Success(Unit)
            val encoded =
                json.encodeToString(
                    serializer,
                    next.grants.entries.associate { (level, at) ->
                        level.name to
                            at.toEpochMilliseconds()
                    },
                )
            when (store.put(KEY, encoded)) {
                is AppResult.Success -> {
                    state.value = next
                    AppResult.Success(Unit)
                }

                is AppResult.Failure -> {
                    AppResult.Failure(AppError.Storage)
                }
            }
        }
    }

    companion object {
        const val KEY: String = "ai.consents.v1"
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = MapSerializer(String.serializer(), Long.serializer())
    }
}
