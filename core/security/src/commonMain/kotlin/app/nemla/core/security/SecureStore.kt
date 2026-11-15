package app.nemla.core.security

import app.nemla.core.common.AppResult

interface SecureStore {
    suspend fun get(key: String): AppResult<String?>

    suspend fun put(
        key: String,
        value: String,
    ): AppResult<Unit>

    suspend fun remove(key: String): AppResult<Unit>

    suspend fun clear(): AppResult<Unit>
}
