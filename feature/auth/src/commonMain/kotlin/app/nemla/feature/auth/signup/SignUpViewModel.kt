package app.nemla.feature.auth.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.domain.model.SignUpOutcome
import app.nemla.core.domain.usecase.SignUp
import app.nemla.core.domain.usecase.SignUpForm
import app.nemla.core.domain.usecase.ValidateCredentials
import app.nemla.core.domain.validation.CredentialRules
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

internal class SignUpViewModel(
    private val signUp: SignUp,
    private val validateCredentials: ValidateCredentials,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<SignUpEffect>(Channel.BUFFERED)
    val effects: Flow<SignUpEffect> = effectChannel.receiveAsFlow()

    fun onAction(action: SignUpAction) {
        when (action) {
            is SignUpAction.EmailChanged -> {
                mutableState.update { it.copy(email = action.value, emailError = null) }
            }

            is SignUpAction.PasswordChanged -> {
                mutableState.update { it.copy(password = action.value, passwordError = null) }
            }

            is SignUpAction.PseudoChanged -> {
                mutableState.update { it.copy(pseudo = action.value) }
            }

            is SignUpAction.AgeChanged -> {
                val digits = action.value.filter { it in '0'..'9' }.take(AGE_MAX_DIGITS)
                mutableState.update { it.copy(age = digits, ageError = null) }
            }

            is SignUpAction.GenderSelected -> {
                mutableState.update { it.copy(gender = action.value) }
            }

            is SignUpAction.HouseholdSelected -> {
                mutableState.update { it.copy(household = action.value) }
            }

            SignUpAction.Submit -> {
                submit()
            }

            SignUpAction.SignInClicked -> {
                if (!mutableState.value.isSubmitting) viewModelScope.launch { effectChannel.send(SignUpEffect.OpenSignIn) }
            }
        }
    }

    private fun submit() {
        val current = mutableState.value
        if (current.isSubmitting) return
        val credentials = validateCredentials(current.email, current.password)
        val age = current.age.takeIf { it.isNotEmpty() }?.toIntOrNull()
        val ageError = if (current.age.isNotEmpty() && age == null) ValidationCode.AGE_OUT_OF_RANGE else CredentialRules.ageError(age)
        if (!credentials.isValid || ageError != null) {
            mutableState.update {
                it.copy(emailError = credentials.email, passwordError = credentials.password, ageError = ageError, banner = null)
            }
            return
        }
        mutableState.update { it.copy(isSubmitting = true, emailError = null, passwordError = null, ageError = null, banner = null) }
        val form =
            SignUpForm(
                email = current.email,
                password = current.password,
                pseudo = current.pseudo,
                age = age,
                gender = current.gender,
                household = current.household,
            )
        viewModelScope.launch {
            when (val result = signUp(form)) {
                is AppResult.Success -> {
                    when (result.value) {
                        is SignUpOutcome.SignedIn -> {
                            mutableState.update { it.copy(isSubmitting = false, password = "") }
                            effectChannel.send(SignUpEffect.SignedUp)
                        }

                        SignUpOutcome.ConfirmationRequired -> {
                            mutableState.update {
                                it.copy(
                                    isSubmitting = false,
                                    password = "",
                                    banner = AuthBanner(AuthBannerKind.Info, AuthMessage.CONFIRMATION_REQUIRED),
                                )
                            }
                        }
                    }
                }

                is AppResult.Failure -> {
                    mutableState.update { it.withFailure(result.error.toAuthFailure()) }
                }
            }
        }
    }

    override fun onCleared() {
        mutableState.update { it.copy(password = "") }
    }
}

private fun SignUpUiState.withFailure(failure: AuthFailure): SignUpUiState =
    when (failure) {
        is AuthFailure.Banner -> {
            copy(isSubmitting = false, banner = failure.banner)
        }

        is AuthFailure.Field -> {
            when (failure.field) {
                AuthField.Email -> copy(isSubmitting = false, emailError = failure.code)
                AuthField.Password -> copy(isSubmitting = false, passwordError = failure.code)
                AuthField.Age -> copy(isSubmitting = false, ageError = failure.code)
            }
        }
    }
