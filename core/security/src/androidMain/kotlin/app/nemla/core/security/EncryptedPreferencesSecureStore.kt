package app.nemla.core.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.nemla.core.common.AppError
import app.nemla.core.common.AppLogger
import app.nemla.core.common.AppResult
import app.nemla.core.common.LogAttribute
import app.nemla.core.common.warn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

internal class EncryptedPreferencesSecureStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: ValueCipher,
    private val dispatcher: CoroutineDispatcher,
    private val logger: AppLogger,
) : SecureStore {
    override suspend fun get(key: String): AppResult<String?> =
        withContext(dispatcher) {
            val preferenceKey = stringPreferencesKey(key)
            val stored =
                guard(SecureStoreLog.READ_FAILED) { dataStore.data.first()[preferenceKey] }
                    ?: return@withContext AppResult.Failure(AppError.Storage)
            val encoded = stored.value ?: return@withContext AppResult.Success(null)
            try {
                AppResult.Success(cipher.decrypt(Base64.decode(encoded), key.encodeToByteArray()).decodeToString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(SecureStoreLog.UNREADABLE_ENTRY_DISCARDED, LogAttribute.errorType(e))
                guard(SecureStoreLog.WRITE_FAILED) { dataStore.edit { it.remove(preferenceKey) } }
                AppResult.Success(null)
            }
        }

    override suspend fun put(
        key: String,
        value: String,
    ): AppResult<Unit> =
        withContext(dispatcher) {
            guard(SecureStoreLog.WRITE_FAILED) {
                val payload = Base64.encode(cipher.encrypt(value.encodeToByteArray(), key.encodeToByteArray()))
                dataStore.edit { it[stringPreferencesKey(key)] = payload }
            }.toUnitResult()
        }

    override suspend fun remove(key: String): AppResult<Unit> =
        withContext(dispatcher) {
            guard(SecureStoreLog.WRITE_FAILED) { dataStore.edit { it.remove(stringPreferencesKey(key)) } }.toUnitResult()
        }

    override suspend fun clear(): AppResult<Unit> =
        withContext(dispatcher) {
            guard(SecureStoreLog.CLEAR_FAILED) { dataStore.edit { it.clear() } }.toUnitResult()
        }

    private class Guarded<T>(
        val value: T,
    )

    private suspend fun <T> guard(
        event: SecureStoreLog,
        block: suspend () -> T,
    ): Guarded<T>? =
        try {
            Guarded(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(event, LogAttribute.errorType(e))
            null
        }

    private fun Guarded<*>?.toUnitResult(): AppResult<Unit> =
        if (this ==
            null
        ) {
            AppResult.Failure(AppError.Storage)
        } else {
            AppResult.Success(Unit)
        }

    override fun toString(): String = "EncryptedPreferencesSecureStore(***)"
}

internal const val SECURE_STORE_KEY_ALIAS = "app.nemla.securestore.aes256gcm.v1"
internal const val SECURE_STORE_FILE_NAME = "nemla_secure_store.preferences_pb"

internal fun createEncryptedPreferencesDataStore(
    file: () -> File,
    dispatcher: CoroutineDispatcher,
): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(dispatcher + SupervisorJob()),
        produceFile = file,
    )

internal fun createAndroidSecureStore(
    context: Context,
    dispatcher: CoroutineDispatcher,
    logger: AppLogger,
): SecureStore =
    EncryptedPreferencesSecureStore(
        dataStore = createEncryptedPreferencesDataStore({ File(context.noBackupFilesDir, SECURE_STORE_FILE_NAME) }, dispatcher),
        cipher = AesGcmValueCipher(AndroidKeystoreKeyProvider(SECURE_STORE_KEY_ALIAS)),
        dispatcher = dispatcher,
        logger = logger,
    )
