package app.nemla.feature.auth.common

import app.nemla.core.common.AppError
import app.nemla.core.common.ValidationCode

internal enum class AuthMessage {
    GENERIC,
    INVALID_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    EMAIL_TAKEN,
    OFFLINE,
    TIMEOUT,
    RATE_LIMITED,
    CONFIGURATION,
    RESET_LINK_SENT,
    CONFIRMATION_REQUIRED,
}

internal enum class AuthBannerKind { Error, Info, Success }

internal data class AuthBanner(
    val kind: AuthBannerKind,
    val message: AuthMessage,
)

internal enum class AuthField { Email, Password, Age }

internal sealed interface AuthFailure {
    data class Field(
        val field: AuthField,
        val code: ValidationCode,
    ) : AuthFailure

    data class Banner(
        val banner: AuthBanner,
    ) : AuthFailure
}

internal fun AppError.toAuthFailure(): AuthFailure =
    when (this) {
        is AppError.Validation -> code.toAuthFailure()
        AppError.Network.Offline -> errorBanner(AuthMessage.OFFLINE)
        AppError.Network.Timeout -> errorBanner(AuthMessage.TIMEOUT)
        AppError.RateLimited -> errorBanner(AuthMessage.RATE_LIMITED)
        AppError.Configuration -> errorBanner(AuthMessage.CONFIGURATION)
        else -> errorBanner(AuthMessage.GENERIC)
    }

private fun ValidationCode.toAuthFailure(): AuthFailure =
    when (this) {
        ValidationCode.EMAIL_REQUIRED, ValidationCode.EMAIL_INVALID -> AuthFailure.Field(AuthField.Email, this)

        ValidationCode.PASSWORD_REQUIRED,
        ValidationCode.PASSWORD_TOO_SHORT,
        ValidationCode.PASSWORD_TOO_LONG,
        ValidationCode.PASSWORD_TOO_WEAK,
        -> AuthFailure.Field(AuthField.Password, this)

        ValidationCode.AGE_OUT_OF_RANGE -> AuthFailure.Field(AuthField.Age, this)

        ValidationCode.INVALID_CREDENTIALS -> errorBanner(AuthMessage.INVALID_CREDENTIALS)

        ValidationCode.EMAIL_NOT_CONFIRMED -> errorBanner(AuthMessage.EMAIL_NOT_CONFIRMED)

        ValidationCode.EMAIL_ALREADY_REGISTERED, ValidationCode.ALREADY_EXISTS -> errorBanner(AuthMessage.EMAIL_TAKEN)

        else -> errorBanner(AuthMessage.GENERIC)
    }

private fun errorBanner(message: AuthMessage): AuthFailure = AuthFailure.Banner(AuthBanner(AuthBannerKind.Error, message))
