package app.nemla.core.domain.model

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.validation.CredentialRules

class EmailAddress private constructor(
    val value: String,
) {
    override fun equals(other: Any?): Boolean = other is EmailAddress && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "EmailAddress(***)"

    companion object {
        fun parse(raw: String): AppResult<EmailAddress> {
            val normalized = raw.trim()
            val error = CredentialRules.emailError(normalized)
            return if (error == null) AppResult.Success(EmailAddress(normalized)) else AppResult.Failure(AppError.Validation(error))
        }
    }
}

class Password private constructor(
    val value: String,
) {
    override fun equals(other: Any?): Boolean = other is Password && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "Password(***)"

    companion object {
        fun parse(raw: String): AppResult<Password> {
            val error = CredentialRules.passwordError(raw)
            return if (error == null) AppResult.Success(Password(raw)) else AppResult.Failure(AppError.Validation(error))
        }
    }
}
