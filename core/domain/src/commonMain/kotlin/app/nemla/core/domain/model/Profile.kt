package app.nemla.core.domain.model

enum class Gender { FEMALE, MALE }

enum class Household { SINGLE, COUPLE, FAMILY, FLATSHARE }

enum class BudgetPeriod { MONTH, YEAR }

data class GlobalBudget(
    val monthlyCents: Long,
    val annualCents: Long?,
    val period: BudgetPeriod,
)

class UserProfile(
    val userId: UserId,
    val pseudo: String?,
    val age: Int?,
    val gender: Gender?,
    val household: Household?,
    val currency: Currency,
    val budget: GlobalBudget,
    val onboardingDone: Boolean,
    val dailyReminder: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        other is UserProfile &&
            other.userId == userId &&
            other.pseudo == pseudo &&
            other.age == age &&
            other.gender == gender &&
            other.household == household &&
            other.currency == currency &&
            other.budget == budget &&
            other.onboardingDone == onboardingDone &&
            other.dailyReminder == dailyReminder

    override fun hashCode(): Int =
        listOf(userId, pseudo, age, gender, household, currency, budget, onboardingDone, dailyReminder).hashCode()

    override fun toString(): String = "UserProfile(***)"
}

class ProfileIdentity(
    val pseudo: String?,
    val age: Int?,
    val gender: Gender?,
    val household: Household?,
    val currency: Currency,
) {
    override fun equals(other: Any?): Boolean =
        other is ProfileIdentity &&
            other.pseudo == pseudo &&
            other.age == age &&
            other.gender == gender &&
            other.household == household &&
            other.currency == currency

    override fun hashCode(): Int = listOf(pseudo, age, gender, household, currency).hashCode()

    override fun toString(): String = "ProfileIdentity(***)"
}

class PseudoAvailability(
    val available: Boolean,
    val suggestions: List<String>,
) {
    override fun equals(other: Any?): Boolean =
        other is PseudoAvailability && other.available == available && other.suggestions == suggestions

    override fun hashCode(): Int = 31 * available.hashCode() + suggestions.hashCode()

    override fun toString(): String = "PseudoAvailability(available=$available, suggestions=${suggestions.size})"
}
