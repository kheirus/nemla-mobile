package app.nemla.feature.auth.forgot

import app.nemla.core.common.ValidationCode
import app.nemla.feature.auth.common.AuthBanner

internal data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: ValidationCode? = null,
    val isSubmitting: Boolean = false,
    val banner: AuthBanner? = null,
) {
    override fun toString(): String = "ForgotPasswordUiState(emailError=$emailError, isSubmitting=$isSubmitting, banner=$banner)"
}

internal sealed interface ForgotPasswordAction {
    data class EmailChanged(
        val value: String,
    ) : ForgotPasswordAction {
        override fun toString(): String = "EmailChanged(***)"
    }

    data object Submit : ForgotPasswordAction

    data object SignUpClicked : ForgotPasswordAction
}

internal sealed interface ForgotPasswordEffect {
    data object LinkSent : ForgotPasswordEffect

    data object OpenSignUp : ForgotPasswordEffect
}
