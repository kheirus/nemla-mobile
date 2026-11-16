package app.nemla.feature.auth.common

import androidx.compose.runtime.Composable
import app.nemla.core.common.ValidationCode
import app.nemla.core.designsystem.component.NemlaBannerKind
import app.nemla.core.domain.validation.CredentialRules
import app.nemla.feature.auth.resources.Res
import app.nemla.feature.auth.resources.auth_confirmation_required
import app.nemla.feature.auth.resources.auth_error_configuration
import app.nemla.feature.auth.resources.auth_error_email_not_confirmed
import app.nemla.feature.auth.resources.auth_error_email_taken
import app.nemla.feature.auth.resources.auth_error_generic
import app.nemla.feature.auth.resources.auth_error_invalid_credentials
import app.nemla.feature.auth.resources.auth_error_offline
import app.nemla.feature.auth.resources.auth_error_rate_limited
import app.nemla.feature.auth.resources.auth_error_timeout
import app.nemla.feature.auth.resources.auth_field_age_out_of_range
import app.nemla.feature.auth.resources.auth_field_email_invalid
import app.nemla.feature.auth.resources.auth_field_email_required
import app.nemla.feature.auth.resources.auth_field_password_required
import app.nemla.feature.auth.resources.auth_field_password_too_long
import app.nemla.feature.auth.resources.auth_field_password_too_short
import app.nemla.feature.auth.resources.auth_field_password_too_weak
import app.nemla.feature.auth.resources.auth_reset_link_sent
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun fieldErrorText(code: ValidationCode?): String? =
    when (code) {
        null -> {
            null
        }

        ValidationCode.EMAIL_REQUIRED -> {
            stringResource(Res.string.auth_field_email_required)
        }

        ValidationCode.EMAIL_INVALID -> {
            stringResource(Res.string.auth_field_email_invalid)
        }

        ValidationCode.PASSWORD_REQUIRED -> {
            stringResource(Res.string.auth_field_password_required)
        }

        ValidationCode.PASSWORD_TOO_SHORT -> {
            stringResource(Res.string.auth_field_password_too_short, CredentialRules.PASSWORD_MIN_LENGTH)
        }

        ValidationCode.PASSWORD_TOO_LONG -> {
            stringResource(Res.string.auth_field_password_too_long, CredentialRules.PASSWORD_MAX_BYTES)
        }

        ValidationCode.PASSWORD_TOO_WEAK -> {
            stringResource(Res.string.auth_field_password_too_weak)
        }

        ValidationCode.AGE_OUT_OF_RANGE -> {
            stringResource(Res.string.auth_field_age_out_of_range, CredentialRules.AGE_MIN, CredentialRules.AGE_MAX)
        }

        else -> {
            stringResource(Res.string.auth_error_generic)
        }
    }

internal val AuthMessage.resource: StringResource
    get() =
        when (this) {
            AuthMessage.GENERIC -> Res.string.auth_error_generic
            AuthMessage.INVALID_CREDENTIALS -> Res.string.auth_error_invalid_credentials
            AuthMessage.EMAIL_NOT_CONFIRMED -> Res.string.auth_error_email_not_confirmed
            AuthMessage.EMAIL_TAKEN -> Res.string.auth_error_email_taken
            AuthMessage.OFFLINE -> Res.string.auth_error_offline
            AuthMessage.TIMEOUT -> Res.string.auth_error_timeout
            AuthMessage.RATE_LIMITED -> Res.string.auth_error_rate_limited
            AuthMessage.CONFIGURATION -> Res.string.auth_error_configuration
            AuthMessage.RESET_LINK_SENT -> Res.string.auth_reset_link_sent
            AuthMessage.CONFIRMATION_REQUIRED -> Res.string.auth_confirmation_required
        }

internal val AuthBannerKind.designKind: NemlaBannerKind
    get() =
        when (this) {
            AuthBannerKind.Error -> NemlaBannerKind.Error
            AuthBannerKind.Info -> NemlaBannerKind.Info
            AuthBannerKind.Success -> NemlaBannerKind.Success
        }
