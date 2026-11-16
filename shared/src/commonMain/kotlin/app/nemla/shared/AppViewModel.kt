package app.nemla.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.usecase.ObserveAuthState
import app.nemla.feature.auth.navigation.AuthEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

internal class AppViewModel(
    observeAuthState: ObserveAuthState,
) : ViewModel() {
    val authState: StateFlow<AuthState> =
        observeAuthState().stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Unknown)
}

internal sealed interface RootTarget {
    data class Auth(
        val entry: AuthEntry,
    ) : RootTarget

    data object Home : RootTarget
}

internal fun rootTargetFor(
    state: AuthState,
    hadSession: Boolean,
): RootTarget? =
    when (state) {
        AuthState.Unknown -> null
        AuthState.SignedOut -> RootTarget.Auth(if (hadSession) AuthEntry.SignIn else AuthEntry.SignUp)
        is AuthState.SignedIn -> RootTarget.Home
    }
