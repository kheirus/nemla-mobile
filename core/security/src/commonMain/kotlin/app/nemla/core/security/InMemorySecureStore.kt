package app.nemla.core.security

import app.nemla.core.common.AppResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemorySecureStore(
    initial: Map<String, String> = emptyMap(),
) : SecureStore {
    private val mutex = Mutex()
    private val values = initial.toMutableMap()

    override suspend fun get(key: String): AppResult<String?> = mutex.withLock { AppResult.Success(values[key]) }

    override suspend fun put(
        key: String,
        value: String,
    ): AppResult<Unit> =
        mutex.withLock {
            values[key] = value
            AppResult.Success(Unit)
        }

    override suspend fun remove(key: String): AppResult<Unit> =
        mutex.withLock {
            values.remove(key)
            AppResult.Success(Unit)
        }

    override suspend fun clear(): AppResult<Unit> =
        mutex.withLock {
            values.clear()
            AppResult.Success(Unit)
        }

    suspend fun snapshot(): Map<String, String> = mutex.withLock { values.toMap() }

    override fun toString(): String = "InMemorySecureStore(***)"
}
