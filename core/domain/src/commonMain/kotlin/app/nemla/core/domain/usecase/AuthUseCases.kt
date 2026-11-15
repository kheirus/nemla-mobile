package app.nemla.core.domain.usecase

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.common.flatMap
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.EmailAddress
import app.nemla.core.domain.model.Gender
import app.nemla.core.domain.model.Household
import app.nemla.core.domain.model.Password
import app.nemla.core.domain.model.Session
import app.nemla.core.domain.model.SignUpDetails
import app.nemla.core.domain.model.SignUpOutcome
import app.nemla.core.domain.model.SignUpRequest
import app.nemla.core.domain.port.AuthRepository
import app.nemla.core.domain.validation.CredentialRules
import kotlinx.coroutines.flow.Flow

data class CredentialErrors(
    val email: ValidationCode?,
    val password: ValidationCode?,
) {
    val isValid: Boolean get() = email == null && password == null
}

class SignUpForm(
    val email: String,
    val password: String,
    val pseudo: String? = null,
    val age: Int? = null,
    val gender: Gender? = null,
    val household: Household? = null,
) {
    override fun toString(): String = "SignUpForm(***)"
}

class ValidateCredentials {
    operator fun invoke(
        email: String,
        password: String,
    ): CredentialErrors = CredentialErrors(CredentialRules.emailError(email), CredentialRules.passwordError(password))
}

class SignIn(
    private val auth: AuthRepository,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
    ): AppResult<Session> =
        EmailAddress.parse(email).flatMap { address ->
            Password.parse(password).flatMap { secret -> auth.signIn(address, secret) }
        }
}

class SignUp(
    private val auth: AuthRepository,
) {
    suspend operator fun invoke(form: SignUpForm): AppResult<SignUpOutcome> {
        val ageError = CredentialRules.ageError(form.age)
        if (ageError != null) return AppResult.Failure(AppError.Validation(ageError))
        val details =
            SignUpDetails(
                pseudo = form.pseudo?.trim()?.takeIf { it.isNotEmpty() },
                age = form.age,
                gender = form.gender,
                household = form.household,
            )
        return EmailAddress.parse(form.email).flatMap { address ->
            Password.parse(form.password).flatMap { secret ->
                auth.signUp(SignUpRequest(address, secret, details))
            }
        }
    }
}

class SignOut(
    private val auth: AuthRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> = auth.signOut()
}

class RequestPasswordReset(
    private val auth: AuthRepository,
) {
    suspend operator fun invoke(email: String): AppResult<Unit> = EmailAddress.parse(email).flatMap { auth.requestPasswordReset(it) }
}

class ObserveAuthState(
    private val auth: AuthRepository,
) {
    operator fun invoke(): Flow<AuthState> = auth.observeAuthState()
}
