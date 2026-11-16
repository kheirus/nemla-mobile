package app.nemla.feature.auth.signin

import app.nemla.core.common.ValidationCode
import app.nemla.feature.auth.common.AuthBanner

internal data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val emailError: ValidationCode? = null,
    val passwordError: ValidationCode? = null,
    val isSubmitting: Boolean = false,
    val banner: AuthBanner? = null,
) {
    override fun toString(): String =
        "SignInUiState(emailError=$emailError, passwordError=$passwordError, isSubmitting=$isSubmitting, banner=$banner)"
}

internal sealed interface SignInAction {
    data class EmailChanged(
        val value: String,
    ) : SignInAction {
        override fun toString(): String = "EmailChanged(***)"
    }

    data class PasswordChanged(
        val value: String,
    ) : SignInAction {
        override fun toString(): String = "PasswordChanged(***)"
    }

    data object Submit : SignInAction

    data object ForgotPasswordClicked : SignInAction

    data object SignUpClicked : SignInAction
}

internal sealed interface SignInEffect {
    data object SignedIn : SignInEffect

    data object OpenForgotPassword : SignInEffect

    data object OpenSignUp : SignInEffect
}
