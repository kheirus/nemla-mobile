package app.nemla.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.domain.model.AppPreferences
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.StartDestination
import app.nemla.core.domain.model.UserId
import app.nemla.core.domain.usecase.LoadPreferences
import app.nemla.core.domain.usecase.ObserveAuthState
import app.nemla.core.domain.usecase.ObservePreferences
import app.nemla.core.domain.usecase.ResolveStartDestination
import app.nemla.feature.auth.navigation.AuthEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class AppViewModel(
    observeAuthState: ObserveAuthState,
    private val resolveStart: ResolveStartDestination,
    observePreferences: ObservePreferences,
    loadPreferences: LoadPreferences,
) : ViewModel() {
    val authState: StateFlow<AuthState> =
        observeAuthState().stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Unknown)

    val preferences: StateFlow<AppPreferences?> = observePreferences()

    private val mutableTarget = MutableStateFlow<RootTarget?>(null)
    val target: StateFlow<RootTarget?> = mutableTarget.asStateFlow()

    private var hadSession = false
    private var resolvedUser: UserId? = null

    init {
        viewModelScope.launch { loadPreferences() }
        viewModelScope.launch { authState.collectLatest(::onAuthState) }
    }

    fun onOnboardingFinished() {
        mutableTarget.value = RootTarget.Home
    }

    private suspend fun onAuthState(state: AuthState) {
        when (state) {
            AuthState.Unknown -> {
                Unit
            }

            AuthState.SignedOut -> {
                resolvedUser = null
                mutableTarget.value = rootTargetFor(state, hadSession)
            }

            is AuthState.SignedIn -> {
                hadSession = true
                val user = state.session.userId
                if (user == resolvedUser) return
                val destination = resolveStart()
                resolvedUser = user
                mutableTarget.value = if (destination == StartDestination.ONBOARDING) RootTarget.Onboarding else RootTarget.Home
            }
        }
    }
}

internal sealed interface RootTarget {
    data class Auth(
        val entry: AuthEntry,
    ) : RootTarget

    data object Home : RootTarget

    data object Onboarding : RootTarget
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
