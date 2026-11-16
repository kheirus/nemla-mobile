package app.nemla.feature.settings.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.AppPreferences
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.ThemeMode
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.GetUserProfile
import app.nemla.core.domain.usecase.ObserveAiConsents
import app.nemla.core.domain.usecase.ObservePreferences
import app.nemla.core.domain.usecase.RevokeAllAiConsents
import app.nemla.core.domain.usecase.UpdateCurrency
import app.nemla.core.domain.usecase.UpdatePreferences
import app.nemla.feature.settings.common.Notice
import app.nemla.feature.settings.common.error
import app.nemla.feature.settings.common.success
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface PreferencesMessage {
    data object CurrencyUpdated : PreferencesMessage

    data class DictationLanguageSet(
        val language: AppLanguage,
    ) : PreferencesMessage

    data object ConsentRevoked : PreferencesMessage

    data object Failed : PreferencesMessage
}

internal data class PreferencesUiState(
    val currency: Currency = Currency.Default,
    val appLanguage: AppLanguage = AppLanguage.FR,
    val dictationLanguage: AppLanguage = AppLanguage.FR,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val aiConsentGranted: Boolean = false,
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

    data object NoticeDismissed : PreferencesAction
}

internal class PreferencesViewModel(
    private val getProfile: GetUserProfile,
    private val updateCurrency: UpdateCurrency,
    observePreferences: ObservePreferences,
    private val updatePreferences: UpdatePreferences,
    private val languages: CurrentLanguages,
    observeConsents: ObserveAiConsents,
    private val revokeConsents: RevokeAllAiConsents,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PreferencesUiState())
    val state: StateFlow<PreferencesUiState> = mutableState.asStateFlow()

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
            PreferencesAction.Opened -> loadCurrency()
            is PreferencesAction.CurrencySelected -> changeCurrency(action.currency)
            is PreferencesAction.AppLanguageSelected -> update { it.copy(appLanguage = action.language) }
            is PreferencesAction.DictationLanguageSelected -> changeDictation(action.language)
            is PreferencesAction.ThemeSelected -> update { it.copy(theme = action.theme) }
            PreferencesAction.RevokeConsentClicked -> revoke()
            PreferencesAction.NoticeDismissed -> mutableState.update { it.copy(notice = null) }
        }
    }

    private fun loadCurrency() {
        viewModelScope.launch {
            val profile = getProfile()
            if (profile is AppResult.Success) mutableState.update { it.copy(currency = profile.value.currency) }
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
