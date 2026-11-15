package app.nemla.core.domain.model

import kotlin.time.Instant

data class Session(
    val userId: UserId,
    val email: EmailAddress?,
    val expiresAt: Instant?,
)

sealed interface AuthState {
    data object Unknown : AuthState

    data object SignedOut : AuthState

    data class SignedIn(
        val session: Session,
    ) : AuthState
}

sealed interface SignUpOutcome {
    data class SignedIn(
        val session: Session,
    ) : SignUpOutcome

    data object ConfirmationRequired : SignUpOutcome
}

class SignUpDetails(
    val pseudo: String?,
    val age: Int?,
    val gender: Gender?,
    val household: Household?,
) {
    override fun equals(other: Any?): Boolean =
        other is SignUpDetails &&
            other.pseudo == pseudo &&
            other.age == age &&
            other.gender == gender &&
            other.household == household

    override fun hashCode(): Int = listOf(pseudo, age, gender, household).hashCode()

    override fun toString(): String = "SignUpDetails(***)"

    companion object {
        val Empty: SignUpDetails = SignUpDetails(pseudo = null, age = null, gender = null, household = null)
    }
}

data class SignUpRequest(
    val email: EmailAddress,
    val password: Password,
    val details: SignUpDetails,
)
