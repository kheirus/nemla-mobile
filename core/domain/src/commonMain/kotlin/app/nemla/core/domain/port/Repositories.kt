package app.nemla.core.domain.port

import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AccountExport
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AiConsents
import app.nemla.core.domain.model.AuthState
import app.nemla.core.domain.model.Category
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.CategoryOutcome
import app.nemla.core.domain.model.CategoryUpdate
import app.nemla.core.domain.model.ClassificationCorrection
import app.nemla.core.domain.model.EmailAddress
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.MonthlyQuota
import app.nemla.core.domain.model.NewCategory
import app.nemla.core.domain.model.Password
import app.nemla.core.domain.model.ProfileIdentity
import app.nemla.core.domain.model.PseudoAvailability
import app.nemla.core.domain.model.ReviewedExpense
import app.nemla.core.domain.model.Session
import app.nemla.core.domain.model.SignUpOutcome
import app.nemla.core.domain.model.SignUpRequest
import app.nemla.core.domain.model.UserProfile
import app.nemla.core.domain.model.VoiceEntryId
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeAuthState(): Flow<AuthState>

    suspend fun signIn(
        email: EmailAddress,
        password: Password,
    ): AppResult<Session>

    suspend fun signUp(request: SignUpRequest): AppResult<SignUpOutcome>

    suspend fun signOut(): AppResult<Unit>

    suspend fun requestPasswordReset(email: EmailAddress): AppResult<Unit>

    suspend fun refreshSession(): AppResult<Session>
}

interface ProfileRepository {
    suspend fun bootstrap(): AppResult<Unit>

    suspend fun getProfile(): AppResult<UserProfile>

    suspend fun updateIdentity(identity: ProfileIdentity): AppResult<Unit>

    suspend fun updateBudget(budget: GlobalBudget): AppResult<Unit>

    suspend fun setDailyReminder(enabled: Boolean): AppResult<Unit>

    suspend fun checkPseudo(pseudo: String): AppResult<PseudoAvailability>
}

interface CategoryRepository {
    suspend fun list(): AppResult<List<Category>>

    suspend fun create(category: NewCategory): AppResult<Category>

    suspend fun update(
        id: CategoryId,
        update: CategoryUpdate,
    ): AppResult<Unit>

    suspend fun delete(id: CategoryId): AppResult<Unit>
}

interface QuotaRepository {
    suspend fun currentMonth(): AppResult<MonthlyQuota>
}

interface AiConsentRepository {
    fun observe(): Flow<AiConsents>

    suspend fun current(): AppResult<AiConsents>

    suspend fun grant(level: AiConsentLevel): AppResult<Unit>

    suspend fun revoke(level: AiConsentLevel): AppResult<Unit>
}

interface AccountRepository {
    suspend fun deleteAccount(): AppResult<Unit>

    suspend fun exportData(): AppResult<AccountExport>
}

interface ExpenseReviewRepository {
    suspend fun learnedMerchantKeys(keys: Set<String>): AppResult<Set<String>>

    suspend fun confirm(expense: ReviewedExpense): AppResult<Unit>

    suspend fun delete(id: ExpenseId): AppResult<Unit>

    suspend fun markVoiceEntryConfirmed(id: VoiceEntryId): AppResult<Unit>

    suspend fun recordCategoryOutcome(outcome: CategoryOutcome): AppResult<Unit>

    suspend fun logCorrection(correction: ClassificationCorrection): AppResult<Unit>
}
