package app.nemla.feature.auth.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppResult
import app.nemla.core.domain.usecase.SignIn
import app.nemla.core.domain.usecase.ValidateCredentials
import app.nemla.feature.auth.common.AuthBanner
import app.nemla.feature.auth.common.AuthBannerKind
import app.nemla.feature.auth.common.AuthFailure
import app.nemla.feature.auth.common.AuthField
import app.nemla.feature.auth.common.AuthMessage
import app.nemla.feature.auth.common.toAuthFailure
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SignInViewModel(
    private val signIn: SignIn,
    private val validateCredentials: ValidateCredentials,
    resetLinkSent: Boolean,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            SignInUiState(banner = if (resetLinkSent) AuthBanner(AuthBannerKind.Success, AuthMessage.RESET_LINK_SENT) else null),
        )
    val state: StateFlow<SignInUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<SignInEffect>(Channel.BUFFERED)
    val effects: Flow<SignInEffect> = effectChannel.receiveAsFlow()

    fun onAction(action: SignInAction) {
        when (action) {
            is SignInAction.EmailChanged -> mutableState.update { it.copy(email = action.value, emailError = null) }
            is SignInAction.PasswordChanged -> mutableState.update { it.copy(password = action.value, passwordError = null) }
            SignInAction.Submit -> submit()
            SignInAction.ForgotPasswordClicked -> emit(SignInEffect.OpenForgotPassword)
            SignInAction.SignUpClicked -> emit(SignInEffect.OpenSignUp)
        }
    }

    private fun submit() {
        val current = mutableState.value
        if (current.isSubmitting) return
        val errors = validateCredentials(current.email, current.password)
        if (!errors.isValid) {
            mutableState.update { it.copy(emailError = errors.email, passwordError = errors.password, banner = null) }
            return
        }
        mutableState.update { it.copy(isSubmitting = true, emailError = null, passwordError = null, banner = null) }
        viewModelScope.launch {
            when (val result = signIn(current.email, current.password)) {
                is AppResult.Success -> {
                    mutableState.update { it.copy(isSubmitting = false, password = "") }
                    effectChannel.send(SignInEffect.SignedIn)
                }

                is AppResult.Failure -> {
                    mutableState.update { it.withFailure(result.error.toAuthFailure()) }
                }
            }
        }
    }

    private fun emit(effect: SignInEffect) {
        if (mutableState.value.isSubmitting) return
        viewModelScope.launch { effectChannel.send(effect) }
    }

    override fun onCleared() {
        mutableState.update { it.copy(password = "") }
    }
}

private fun SignInUiState.withFailure(failure: AuthFailure): SignInUiState =
    when (failure) {
        is AuthFailure.Banner -> {
            copy(isSubmitting = false, banner = failure.banner)
        }

        is AuthFailure.Field -> {
            when (failure.field) {
                AuthField.Email -> copy(isSubmitting = false, emailError = failure.code)
                AuthField.Password, AuthField.Age -> copy(isSubmitting = false, passwordError = failure.code)
            }
        }
    }
