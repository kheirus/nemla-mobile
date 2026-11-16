package app.nemla.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppResult
import app.nemla.core.common.getOrNull
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.usecase.SignOut
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class LoadGreetingName(
    private val profiles: ProfileRepository,
) {
    suspend operator fun invoke(): String? {
        profiles.bootstrap()
        return profiles
            .getProfile()
            .getOrNull()
            ?.pseudo
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}

internal class HomeViewModel(
    private val loadGreetingName: LoadGreetingName,
    private val signOut: SignOut,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val pseudo = loadGreetingName()
            mutableState.update { it.copy(pseudo = pseudo) }
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.SignOutClicked -> signOutOnce()
        }
    }

    private fun signOutOnce() {
        if (mutableState.value.isSigningOut) return
        mutableState.update { it.copy(isSigningOut = true, signOutFailed = false) }
        viewModelScope.launch {
            when (signOut()) {
                is AppResult.Success -> {
                    mutableState.update { it.copy(isSigningOut = false) }
                    effectChannel.send(HomeEffect.SignedOut)
                }

                is AppResult.Failure -> {
                    mutableState.update { it.copy(isSigningOut = false, signOutFailed = true) }
                }
            }
        }
    }
}
