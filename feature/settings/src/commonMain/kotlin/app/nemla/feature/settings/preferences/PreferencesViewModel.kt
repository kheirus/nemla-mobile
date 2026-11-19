package app.nemla.feature.settings.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.AppPreferences
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.NotificationAccess
import app.nemla.core.domain.model.ThemeMode
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.GetNotificationAccess
import app.nemla.core.domain.usecase.GetUserProfile
import app.nemla.core.domain.usecase.ObserveAiConsents
import app.nemla.core.domain.usecase.ObservePreferences
import app.nemla.core.domain.usecase.RevokeAllAiConsents
import app.nemla.core.domain.usecase.SetDailyReminder
import app.nemla.core.domain.usecase.UpdateCurrency
import app.nemla.core.domain.usecase.UpdatePreferences
import app.nemla.feature.settings.common.Notice
import app.nemla.feature.settings.common.error
import app.nemla.feature.settings.common.success
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface PreferencesMessage {
    data object CurrencyUpdated : PreferencesMessage

    data class DictationLanguageSet(
        val language: AppLanguage,
    ) : PreferencesMessage

    data object ConsentRevoked : PreferencesMessage

    data object ReminderEnabled : PreferencesMessage

    data object ReminderDisabled : PreferencesMessage

    data object NotificationsDenied : PreferencesMessage

    data object Failed : PreferencesMessage
}

internal data class PreferencesUiState(
    val currency: Currency = Currency.Default,
    val appLanguage: AppLanguage = AppLanguage.FR,
    val dictationLanguage: AppLanguage = AppLanguage.FR,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val aiConsentGranted: Boolean = false,
    val dailyReminder: Boolean = false,
    val isSavingReminder: Boolean = false,
    val isSavingCurrency: Boolean = false,
    val notice: Notice<PreferencesMessage>? = null,
)

internal sealed interface PreferencesAction {
    data object Opened : PreferencesAction

    data class CurrencySelected(
        val currency: Currency,
    ) : PreferencesAction

    data class AppLanguageSelected(
        val language: AppLanguage,
    ) : PreferencesAction

    data class DictationLanguageSelected(
        val language: AppLanguage,
    ) : PreferencesAction

    data class ThemeSelected(
        val theme: ThemeMode,
    ) : PreferencesAction

    data object RevokeConsentClicked : PreferencesAction

    data class ReminderToggled(
        val enabled: Boolean,
    ) : PreferencesAction

    data class NotificationPermissionAnswered(
        val granted: Boolean,
    ) : PreferencesAction

    data object NoticeDismissed : PreferencesAction
}

internal sealed interface PreferencesEffect {
    data object RequestNotificationPermission : PreferencesEffect
}

internal class PreferencesViewModel(
    private val getProfile: GetUserProfile,
    private val updateCurrency: UpdateCurrency,
    observePreferences: ObservePreferences,
    private val updatePreferences: UpdatePreferences,
    private val languages: CurrentLanguages,
    observeConsents: ObserveAiConsents,
    private val revokeConsents: RevokeAllAiConsents,
    private val notificationAccess: GetNotificationAccess,
    private val setDailyReminder: SetDailyReminder,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PreferencesUiState())
    val state: StateFlow<PreferencesUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<PreferencesEffect>(Channel.BUFFERED)
    val effects: Flow<PreferencesEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observePreferences().collect { preferences ->
                mutableState.update {
                    it.copy(
                        appLanguage = languages.ui(),
                        dictationLanguage = languages.dictation(),
                        theme = preferences?.theme ?: ThemeMode.SYSTEM,
                    )
                }
            }
        }
        viewModelScope.launch {
            observeConsents().collect { consents ->
                mutableState.update { it.copy(aiConsentGranted = consents.isGranted(AiConsentLevel.PERSONAL)) }
            }
        }
    }

    fun onAction(action: PreferencesAction) {
        when (action) {
            PreferencesAction.Opened -> loadProfile()
            is PreferencesAction.CurrencySelected -> changeCurrency(action.currency)
            is PreferencesAction.AppLanguageSelected -> update { it.copy(appLanguage = action.language) }
            is PreferencesAction.DictationLanguageSelected -> changeDictation(action.language)
            is PreferencesAction.ThemeSelected -> update { it.copy(theme = action.theme) }
            PreferencesAction.RevokeConsentClicked -> revoke()
            is PreferencesAction.ReminderToggled -> toggleReminder(action.enabled)
            is PreferencesAction.NotificationPermissionAnswered -> answerPermission(action.granted)
            PreferencesAction.NoticeDismissed -> mutableState.update { it.copy(notice = null) }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val profile = getProfile()
            if (profile is AppResult.Success) {
                val reminder = profile.value.dailyReminder && notificationAccess() == NotificationAccess.GRANTED
                mutableState.update {
                    it.copy(
                        currency = profile.value.currency,
                        dailyReminder = if (it.isSavingReminder) it.dailyReminder else reminder,
                    )
                }
            }
        }
    }

    private fun toggleReminder(enabled: Boolean) {
        if (mutableState.value.isSavingReminder) return
        mutableState.update { it.copy(isSavingReminder = true, notice = null) }
        viewModelScope.launch {
            if (!enabled) {
                saveReminder(false)
            } else if (notificationAccess() == NotificationAccess.GRANTED) {
                saveReminder(true)
            } else {
                effectChannel.send(PreferencesEffect.RequestNotificationPermission)
            }
        }
    }

    private fun answerPermission(granted: Boolean) {
        if (!mutableState.value.isSavingReminder) return
        if (granted) {
            viewModelScope.launch { saveReminder(true) }
        } else {
            mutableState.update {
                it.copy(dailyReminder = false, isSavingReminder = false, notice = error(PreferencesMessage.NotificationsDenied))
            }
        }
    }

    private suspend fun saveReminder(enabled: Boolean) {
        val previous = mutableState.value.dailyReminder
        mutableState.update { it.copy(dailyReminder = enabled) }
        val result = setDailyReminder(enabled)
        mutableState.update {
            if (result is AppResult.Success) {
                it.copy(
                    isSavingReminder = false,
                    notice = success(if (enabled) PreferencesMessage.ReminderEnabled else PreferencesMessage.ReminderDisabled),
                )
            } else {
                it.copy(dailyReminder = previous, isSavingReminder = false, notice = error(PreferencesMessage.Failed))
            }
        }
    }

    private fun changeCurrency(currency: Currency) {
        val previous = mutableState.value.currency
        if (currency == previous || mutableState.value.isSavingCurrency) return
        mutableState.update { it.copy(currency = currency, isSavingCurrency = true, notice = null) }
        viewModelScope.launch {
            val result = updateCurrency(currency)
            mutableState.update {
                if (result is AppResult.Success) {
                    it.copy(isSavingCurrency = false, notice = success(PreferencesMessage.CurrencyUpdated))
                } else {
                    it.copy(currency = previous, isSavingCurrency = false, notice = error(PreferencesMessage.Failed))
                }
            }
        }
    }

    private fun changeDictation(language: AppLanguage) {
        viewModelScope.launch {
            val result = updatePreferences { it.copy(dictationLanguage = language) }
            mutableState.update {
                it.copy(
                    notice =
                        if (result is AppResult.Success) {
                            success(
                                PreferencesMessage.DictationLanguageSet(language),
                            )
                        } else {
                            error(PreferencesMessage.Failed)
                        },
                )
            }
        }
    }

    private fun update(transform: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch {
            if (updatePreferences(
                    transform,
                ) is AppResult.Failure
            ) {
                mutableState.update { it.copy(notice = error(PreferencesMessage.Failed)) }
            }
        }
    }

    private fun revoke() {
        viewModelScope.launch {
            val result = revokeConsents()
            mutableState.update {
                it.copy(
                    notice =
                        if (result is AppResult.Success) {
                            success(
                                PreferencesMessage.ConsentRevoked,
                            )
                        } else {
                            error(PreferencesMessage.Failed)
                        },
                )
            }
        }
    }
}
