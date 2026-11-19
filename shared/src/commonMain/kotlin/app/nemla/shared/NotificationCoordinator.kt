package app.nemla.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.UserId
import app.nemla.core.domain.usecase.ClearNotifications
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.DeliverPushInbox
import app.nemla.core.domain.usecase.ObserveAuthState
import app.nemla.core.domain.usecase.ObservePreferences
import app.nemla.core.domain.usecase.RefreshScheduledNotifications
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

internal class NotificationCoordinator(
    observeAuthState: ObserveAuthState,
    observePreferences: ObservePreferences,
    private val languages: CurrentLanguages,
    private val refresh: RefreshScheduledNotifications,
    private val deliver: DeliverPushInbox,
    private val clear: ClearNotifications,
) : ViewModel() {
    private var user: UserId? = null
    private var hadSession = false
    private var language: AppLanguage? = null
    private var syncJob: Job? = null
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch { observeAuthState().collect(::onAuthState) }
        viewModelScope.launch {
            observePreferences().collect { preferences ->
                if (preferences == null) return@collect
                val current = languages.ui()
                val previous = language
                language = current
                if (previous != null && previous != current && user != null) refreshOnly()
            }
        }
    }

    fun onForeground() {
        if (user != null) sync()
    }

    private fun onAuthState(state: AuthState) {
        when (state) {
            AuthState.Unknown -> {
                Unit
            }

            AuthState.SignedOut -> {
                user = null
                if (hadSession) {
                    hadSession = false
                    syncJob?.cancel()
                    refreshJob?.cancel()
                    viewModelScope.launch { clear() }
                }
            }

            is AuthState.SignedIn -> {
                hadSession = true
                val signedIn = state.session.userId
                if (signedIn == user) return
                user = signedIn
                sync()
            }
        }
    }

    private fun sync() {
        if (syncJob?.isActive == true) return
        syncJob =
            viewModelScope.launch {
                refresh()
                deliver()
            }
    }

    private fun refreshOnly() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch { refresh() }
    }
}

@Composable
internal fun NotificationCoordinatorEffect(coordinator: NotificationCoordinator = koinViewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(coordinator, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            coordinator.onForeground()
            awaitCancellation()
        }
    }
}
