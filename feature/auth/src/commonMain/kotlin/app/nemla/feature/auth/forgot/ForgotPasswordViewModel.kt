package app.nemla.feature.auth.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppResult
import app.nemla.core.domain.usecase.RequestPasswordReset
import app.nemla.core.domain.validation.CredentialRules
import app.nemla.feature.auth.common.AuthFailure
import app.nemla.feature.auth.common.AuthField
import app.nemla.feature.auth.common.toAuthFailure
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ForgotPasswordViewModel(
    private val requestPasswordReset: RequestPasswordReset,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<ForgotPasswordEffect>(Channel.BUFFERED)
    val effects: Flow<ForgotPasswordEffect> = effectChannel.receiveAsFlow()

    fun onAction(action: ForgotPasswordAction) {
        when (action) {
            is ForgotPasswordAction.EmailChanged -> {
                mutableState.update { it.copy(email = action.value, emailError = null) }
            }

            ForgotPasswordAction.Submit -> {
                submit()
            }

            ForgotPasswordAction.SignUpClicked -> {
                if (!mutableState.value.isSubmitting) viewModelScope.launch { effectChannel.send(ForgotPasswordEffect.OpenSignUp) }
            }
        }
    }

    private fun submit() {
        val current = mutableState.value
        if (current.isSubmitting) return
        val emailError = CredentialRules.emailError(current.email)
        if (emailError != null) {
            mutableState.update { it.copy(emailError = emailError, banner = null) }
            return
        }
        mutableState.update { it.copy(isSubmitting = true, emailError = null, banner = null) }
        viewModelScope.launch {
            when (val result = requestPasswordReset(current.email)) {
                is AppResult.Success -> {
                    mutableState.update { it.copy(isSubmitting = false) }
                    effectChannel.send(ForgotPasswordEffect.LinkSent)
                }

                is AppResult.Failure -> {
                    mutableState.update { state ->
                        when (val failure = result.error.toAuthFailure()) {
                            is AuthFailure.Banner -> {
                                state.copy(isSubmitting = false, banner = failure.banner)
                            }

                            is AuthFailure.Field -> {
                                if (failure.field == AuthField.Email) {
                                    state.copy(isSubmitting = false, emailError = failure.code)
                                } else {
                                    state.copy(isSubmitting = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
