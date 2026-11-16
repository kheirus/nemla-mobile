package app.nemla.core.data.preferences

import app.nemla.core.common.AppError
import app.nemla.core.common.AppLogger
import app.nemla.core.common.AppResult
import app.nemla.core.common.warn
import app.nemla.core.data.DataLog
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.AppPreferences
import app.nemla.core.domain.model.ThemeMode
import app.nemla.core.domain.port.PreferencesRepository
import app.nemla.core.security.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
internal class PreferencesDto(
    val appLanguage: String? = null,
    val dictationLanguage: String? = null,
    val theme: String? = null,
) {
    fun toDomain(): AppPreferences =
        AppPreferences(
            appLanguage = languageOf(appLanguage),
            dictationLanguage = languageOf(dictationLanguage),
            theme = ThemeMode.entries.firstOrNull { it.name == theme } ?: ThemeMode.SYSTEM,
        )

    private fun languageOf(tag: String?): AppLanguage? = AppLanguage.entries.firstOrNull { it.tag == tag }

    companion object {
        fun of(preferences: AppPreferences): PreferencesDto =
            PreferencesDto(preferences.appLanguage?.tag, preferences.dictationLanguage?.tag, preferences.theme.name)
    }
}

internal class SecureStorePreferencesRepository(
    private val store: SecureStore,
    private val logger: AppLogger,
) : PreferencesRepository {
    private val mutex = Mutex()
    private val state = MutableStateFlow<AppPreferences?>(null)
    override val preferences: StateFlow<AppPreferences?> = state.asStateFlow()

    override suspend fun load(): AppPreferences {
        state.value?.let { return it }
        return mutex.withLock { state.value ?: read().also { state.value = it } }
    }

    override suspend fun update(preferences: AppPreferences): AppResult<Unit> {
        load()
        return mutex.withLock {
            if (state.value == preferences) return@withLock AppResult.Success(Unit)
            when (store.put(KEY, json.encodeToString(PreferencesDto.serializer(), PreferencesDto.of(preferences)))) {
                is AppResult.Success -> {
                    state.value = preferences
                    AppResult.Success(Unit)
                }

                is AppResult.Failure -> {
                    AppResult.Failure(AppError.Storage)
                }
            }
        }
    }

    private suspend fun read(): AppPreferences {
        val raw =
            when (val result = store.get(KEY)) {
                is AppResult.Success -> {
                    result.value ?: return AppPreferences.Default
                }

                is AppResult.Failure -> {
                    logger.warn(DataLog.PREFERENCES_READ_FAILED)
                    return AppPreferences.Default
                }
            }
        return try {
            json.decodeFromString(PreferencesDto.serializer(), raw).toDomain()
        } catch (e: SerializationException) {
            logger.warn(DataLog.PREFERENCES_READ_FAILED)
            AppPreferences.Default
        } catch (e: IllegalArgumentException) {
            logger.warn(DataLog.PREFERENCES_READ_FAILED)
            AppPreferences.Default
        }
    }

    companion object {
        const val KEY: String = "preferences.v1"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
