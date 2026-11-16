package app.nemla.feature.auth.signup

import app.nemla.core.common.ValidationCode
import app.nemla.core.domain.model.Gender
import app.nemla.core.domain.model.Household
import app.nemla.feature.auth.common.AuthBanner

internal data class SignUpUiState(
    val email: String = "",
    val password: String = "",
    val pseudo: String = "",
    val age: String = "",
    val gender: Gender? = null,
    val household: Household? = null,
    val emailError: ValidationCode? = null,
    val passwordError: ValidationCode? = null,
    val ageError: ValidationCode? = null,
    val isSubmitting: Boolean = false,
    val banner: AuthBanner? = null,
) {
    override fun toString(): String =
        "SignUpUiState(emailError=$emailError, passwordError=$passwordError, ageError=$ageError, " +
            "isSubmitting=$isSubmitting, banner=$banner)"
}

internal sealed interface SignUpAction {
    data class EmailChanged(
        val value: String,
    ) : SignUpAction {
        override fun toString(): String = "EmailChanged(***)"
    }

    data class PasswordChanged(
        val value: String,
    ) : SignUpAction {
        override fun toString(): String = "PasswordChanged(***)"
    }

    data class PseudoChanged(
        val value: String,
    ) : SignUpAction {
        override fun toString(): String = "PseudoChanged(***)"
    }

    data class AgeChanged(
        val value: String,
    ) : SignUpAction {
        override fun toString(): String = "AgeChanged(***)"
    }

    data class GenderSelected(
        val value: Gender,
    ) : SignUpAction {
        override fun toString(): String = "GenderSelected(***)"
    }

    data class HouseholdSelected(
        val value: Household,
    ) : SignUpAction {
        override fun toString(): String = "HouseholdSelected(***)"
    }

    data object Submit : SignUpAction

    data object SignInClicked : SignUpAction
}

internal sealed interface SignUpEffect {
    data object SignedUp : SignUpEffect

    data object OpenSignIn : SignUpEffect
}

internal const val AGE_MAX_DIGITS = 3
