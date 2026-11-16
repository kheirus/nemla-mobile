package app.nemla.core.domain.usecase

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.AppPreferences
import app.nemla.core.domain.model.Banks
import app.nemla.core.domain.model.BudgetEstimate
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.BudgetRules
import app.nemla.core.domain.model.Category
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.CategoryPalette
import app.nemla.core.domain.model.CategoryType
import app.nemla.core.domain.model.CategoryUpdate
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.DebitSettings
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.model.NewCategory
import app.nemla.core.domain.model.ProfileIdentity
import app.nemla.core.domain.model.UserProfile
import app.nemla.core.domain.port.AccountRepository
import app.nemla.core.domain.port.BudgetAdvisor
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.domain.port.DeferredDebitRepository
import app.nemla.core.domain.port.DeviceLanguage
import app.nemla.core.domain.port.PreferencesRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.validation.CredentialRules
import kotlinx.coroutines.flow.StateFlow

class GetUserProfile(
    private val profiles: ProfileRepository,
) {
    suspend operator fun invoke(): AppResult<UserProfile> = profiles.getProfile()
}

class SaveGlobalBudget(
    private val profiles: ProfileRepository,
) {
    suspend operator fun invoke(
        amountCents: Long,
        period: BudgetPeriod,
    ): AppResult<Unit> {
        if (amountCents <= 0) return AppResult.Failure(AppError.Validation(ValidationCode.INVALID_INPUT))
        return profiles.updateBudget(BudgetRules.globalBudget(amountCents, period))
    }
}

class SuggestBudget(
    private val advisor: BudgetAdvisor,
) {
    suspend operator fun invoke(): AppResult<BudgetEstimate> = advisor.suggest()
}

sealed interface ProfileSaveOutcome {
    data object Saved : ProfileSaveOutcome

    data class PseudoTaken(
        val suggestions: List<String>,
    ) : ProfileSaveOutcome
}

class SaveProfileIdentity(
    private val profiles: ProfileRepository,
) {
    suspend operator fun invoke(identity: ProfileIdentity): AppResult<ProfileSaveOutcome> {
        CredentialRules.ageError(identity.age)?.let { return AppResult.Failure(AppError.Validation(it)) }
        val pseudo = identity.pseudo?.trim().orEmpty()
        if (pseudo.isNotEmpty()) {
            val availability = profiles.checkPseudo(pseudo)
            if (availability is AppResult.Success && !availability.value.available) {
                return AppResult.Success(ProfileSaveOutcome.PseudoTaken(availability.value.suggestions))
            }
        }
        val normalized = ProfileIdentity(pseudo.ifEmpty { null }, identity.age, identity.gender, identity.household, identity.currency)
        return when (val saved = profiles.updateIdentity(normalized)) {
            is AppResult.Success -> AppResult.Success(ProfileSaveOutcome.Saved)
            is AppResult.Failure -> saved
        }
    }
}

class UpdateCurrency(
    private val profiles: ProfileRepository,
) {
    suspend operator fun invoke(currency: Currency): AppResult<Unit> = profiles.updateCurrency(currency)
}

class AddCategory(
    private val categories: CategoryRepository,
) {
    suspend operator fun invoke(
        name: String,
        existing: List<Category>,
    ): AppResult<Category> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AppResult.Failure(AppError.Validation(ValidationCode.INVALID_INPUT))
        val color = CategoryPalette.pickUnusedColor(existing.map { it.colorHex })
        return categories.create(NewCategory(trimmed, color, CategoryType.VARIABLE))
    }
}

class UpdateCategory(
    private val categories: CategoryRepository,
) {
    suspend operator fun invoke(
        id: CategoryId,
        update: CategoryUpdate,
    ): AppResult<Unit> = categories.update(id, update)
}

class DeleteCategory(
    private val categories: CategoryRepository,
) {
    suspend operator fun invoke(category: Category): AppResult<Unit> {
        if (category.isDefault) return AppResult.Failure(AppError.Forbidden)
        return categories.delete(category.id)
    }
}

class GetDebitSettings(
    private val debits: DeferredDebitRepository,
) {
    suspend operator fun invoke(): AppResult<DebitSettings> = debits.settings()
}

class SaveDebitSettings(
    private val debits: DeferredDebitRepository,
) {
    suspend operator fun invoke(
        bankId: String?,
        rule: DeferredDebit,
    ): AppResult<Int> {
        if (rule.enabled && Banks.byId(bankId) == null) return AppResult.Failure(AppError.Validation(ValidationCode.INVALID_INPUT))
        val normalized =
            rule.copy(
                cutoffDay = rule.cutoffDay.coerceIn(Banks.CUTOFF_MIN, Banks.CUTOFF_MAX),
                debitDay = rule.debitDay.coerceIn(Banks.DEBIT_MIN, Banks.DEBIT_MAX),
            )
        return debits.save(bankId.takeIf { rule.enabled }, normalized)
    }
}

class DeleteAccount(
    private val accounts: AccountRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> = accounts.deleteAccount()
}

class ObservePreferences(
    private val preferences: PreferencesRepository,
) {
    operator fun invoke(): StateFlow<AppPreferences?> = preferences.preferences
}

class LoadPreferences(
    private val preferences: PreferencesRepository,
) {
    suspend operator fun invoke(): AppPreferences = preferences.load()
}

class UpdatePreferences(
    private val preferences: PreferencesRepository,
) {
    suspend operator fun invoke(transform: (AppPreferences) -> AppPreferences): AppResult<Unit> {
        val current = preferences.preferences.value ?: preferences.load()
        return preferences.update(transform(current))
    }
}

class CurrentLanguages(
    private val preferences: PreferencesRepository,
    private val device: DeviceLanguage,
) {
    fun ui(): AppLanguage = preferences.preferences.value?.appLanguage ?: device.current()

    fun dictation(): AppLanguage = preferences.preferences.value?.dictationLanguage ?: device.current()
}
