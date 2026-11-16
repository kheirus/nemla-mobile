package app.nemla.core.domain

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.CategoryOutcome
import app.nemla.core.domain.model.ClassificationCorrection
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ReviewedExpense
import app.nemla.core.domain.model.VoiceEntryId
import app.nemla.core.domain.port.ExpenseReviewRepository

internal class FakeExpenseReviewRepository : ExpenseReviewRepository {
    var learned: AppResult<Set<String>> = AppResult.Success(emptySet())
    var confirmResult: AppResult<Unit> = AppResult.Success(Unit)
    var markResult: AppResult<Unit> = AppResult.Success(Unit)
    var outcomeResult: AppResult<Unit> = AppResult.Success(Unit)

    val learnedQueries = mutableListOf<Set<String>>()
    val confirmed = mutableListOf<ReviewedExpense>()
    val deleted = mutableListOf<ExpenseId>()
    val marked = mutableListOf<VoiceEntryId>()
    val outcomes = mutableListOf<CategoryOutcome>()
    val corrections = mutableListOf<ClassificationCorrection>()

    override suspend fun learnedMerchantKeys(keys: Set<String>): AppResult<Set<String>> {
        learnedQueries += keys
        return learned
    }

    override suspend fun confirm(expense: ReviewedExpense): AppResult<Unit> {
        confirmed += expense
        return confirmResult
    }

    override suspend fun delete(id: ExpenseId): AppResult<Unit> {
        deleted += id
        return AppResult.Success(Unit)
    }

    override suspend fun markVoiceEntryConfirmed(id: VoiceEntryId): AppResult<Unit> {
        marked += id
        return markResult
    }

    override suspend fun recordCategoryOutcome(outcome: CategoryOutcome): AppResult<Unit> {
        outcomes += outcome
        return outcomeResult
    }

    override suspend fun logCorrection(correction: ClassificationCorrection): AppResult<Unit> {
        corrections += correction
        return AppResult.Failure(AppError.NotSupportedYet)
    }
}
