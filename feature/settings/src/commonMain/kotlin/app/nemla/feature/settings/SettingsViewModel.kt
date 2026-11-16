package app.nemla.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.usecase.DeleteAccount
import app.nemla.core.domain.usecase.GetUserProfile
import app.nemla.core.domain.usecase.SignOut
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SettingsViewModel(
    initialTab: SettingsTab,
    private val getProfile: GetUserProfile,
    private val deleteAccount: DeleteAccount,
    private val signOut: SignOut,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsUiState(tab = initialTab))
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = effectChannel.receiveAsFlow()

    init {
        refreshProfile()
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.Started -> refreshProfile()
            is SettingsAction.TabSelected -> mutableState.update { it.copy(tab = action.tab, panel = null) }
            is SettingsAction.PanelOpened -> mutableState.update { it.copy(tab = SettingsTab.PROFILE, panel = action.panel) }
            SettingsAction.BackClicked -> back()
            SettingsAction.ProfileChanged -> refreshProfile()
            SettingsAction.CategoryBudgetsRequested -> mutableState.update { it.copy(tab = SettingsTab.CATEGORIES, panel = null) }
            SettingsAction.DeleteAccountClicked -> mutableState.update { it.copy(isConfirmingDelete = true, accountMessage = null) }
            SettingsAction.DeleteAccountCancelled -> mutableState.update { it.copy(isConfirmingDelete = false) }
            SettingsAction.DeleteAccountConfirmed -> delete()
            SettingsAction.AccountMessageDismissed -> mutableState.update { it.copy(accountMessage = null) }
            SettingsAction.SignOutClicked -> signOutOnce()
        }
    }

    private fun back() {
        if (mutableState.value.panel != null) {
            mutableState.update { it.copy(panel = null) }
            refreshProfile()
        } else {
            viewModelScope.launch { effectChannel.send(SettingsEffect.Close) }
        }
    }

    private fun refreshProfile() {
        viewModelScope.launch {
            val result = getProfile()
            if (result is AppResult.Success) {
                val profile = result.value
                mutableState.update {
                    it.copy(
                        pseudo = profile.pseudo?.trim()?.takeIf(String::isNotEmpty),
                        household = profile.household,
                        currency = profile.currency,
                        budget = profile.budget,
                    )
                }
            }
        }
    }

    private fun delete() {
        if (mutableState.value.isDeleting) return
        mutableState.update { it.copy(isConfirmingDelete = false, isDeleting = true, accountMessage = null) }
        viewModelScope.launch {
            when (val result = deleteAccount()) {
                is AppResult.Success -> {
                    signOut()
                    mutableState.update { it.copy(isDeleting = false) }
                    effectChannel.send(SettingsEffect.SignedOut)
                }

                is AppResult.Failure -> {
                    val message =
                        if (result.error ==
                            AppError.NotSupportedYet
                        ) {
                            AccountMessage.DELETE_UNAVAILABLE
                        } else {
                            AccountMessage.DELETE_FAILED
                        }
                    mutableState.update { it.copy(isDeleting = false, accountMessage = message) }
                }
            }
        }
    }

    private fun signOutOnce() {
        if (mutableState.value.isSigningOut) return
        mutableState.update { it.copy(isSigningOut = true) }
        viewModelScope.launch {
            val result = signOut()
            mutableState.update {
                it.copy(isSigningOut = false, accountMessage = if (result is AppResult.Failure) AccountMessage.SIGN_OUT_FAILED else null)
            }
            if (result is AppResult.Success) effectChannel.send(SettingsEffect.SignedOut)
        }
    }
}
