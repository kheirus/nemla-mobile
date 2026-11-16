package app.nemla.core.domain.usecase

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.common.getOrNull
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.Category
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.CategoryOutcome
import app.nemla.core.domain.model.ClassificationCorrection
import app.nemla.core.domain.model.ExpenseDraft
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.MerchantKeys
import app.nemla.core.domain.model.MonthlyQuota
import app.nemla.core.domain.model.ReviewDecision
import app.nemla.core.domain.model.ReviewedExpense
import app.nemla.core.domain.model.VoiceCapture
import app.nemla.core.domain.model.VoiceEntryId
import app.nemla.core.domain.model.VoiceRecording
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.domain.port.ExpenseReviewRepository
import app.nemla.core.domain.port.QuotaRepository
import app.nemla.core.domain.port.VoiceExpenseCapturer

class CaptureVoiceExpenses(
    private val capturer: VoiceExpenseCapturer,
) {
    suspend operator fun invoke(
        recording: VoiceRecording,
        language: AppLanguage,
    ): AppResult<VoiceCapture> = capturer.capture(recording, language)
}

class GetMonthlyQuota(
    private val quotas: QuotaRepository,
) {
    suspend operator fun invoke(): AppResult<MonthlyQuota> = quotas.currentMonth()
}

class ListCategories(
    private val categories: CategoryRepository,
) {
    suspend operator fun invoke(): AppResult<List<Category>> = categories.list()
}

class FindLearnedExpenses(
    private val reviews: ExpenseReviewRepository,
) {
    suspend operator fun invoke(drafts: List<ExpenseDraft>): Set<ExpenseId> {
        val keysById =
            drafts
                .mapNotNull { draft -> draft.id?.let { it to MerchantKeys.normalize(draft.label) } }
                .filter { (_, key) -> MerchantKeys.isUsable(key) }
        if (keysById.isEmpty()) return emptySet()
        val learned = reviews.learnedMerchantKeys(keysById.map { it.second }.toSet()).getOrNull() ?: return emptySet()
        return keysById.filter { (_, key) -> key in learned }.map { it.first }.toSet()
    }
}

class DeletePendingExpense(
    private val reviews: ExpenseReviewRepository,
) {
    suspend operator fun invoke(id: ExpenseId): AppResult<Unit> = reviews.delete(id)
}

class ConfirmReviewedExpenses(
    private val reviews: ExpenseReviewRepository,
) {
    suspend operator fun invoke(
        voiceEntryId: VoiceEntryId?,
        decisions: List<ReviewDecision>,
        categories: List<Category>,
    ): AppResult<Unit> {
        if (decisions.any { it.draft.id == null }) return AppResult.Failure(AppError.Validation(ValidationCode.INVALID_INPUT))
        for (decision in decisions) {
            val confirmed = reviews.confirm(decision.toReviewedExpense())
            if (confirmed is AppResult.Failure) return confirmed
        }
        if (voiceEntryId != null) {
            val marked = reviews.markVoiceEntryConfirmed(voiceEntryId)
            if (marked is AppResult.Failure) return marked
        }
        val names = categories.associate { it.id to it.name }
        decisions.forEach { learnFrom(it, voiceEntryId, names) }
        return AppResult.Success(Unit)
    }

    private suspend fun learnFrom(
        decision: ReviewDecision,
        voiceEntryId: VoiceEntryId?,
        names: Map<CategoryId, String>,
    ) {
        val id = decision.draft.id ?: return
        val suggested = decision.draft.categoryId?.let(names::get)
        val chosen = decision.categoryId?.let(names::get)
        val key = MerchantKeys.normalize(decision.draft.label)
        if (MerchantKeys.isUsable(key)) {
            reviews.recordCategoryOutcome(
                CategoryOutcome(
                    expenseId = id,
                    merchantKey = key,
                    categoryId = decision.categoryId,
                    suggestedCategoryName = suggested,
                    chosenCategoryName = chosen,
                    wasCorrection = decision.categoryChanged,
                ),
            )
        }
        if (decision.categoryChanged || decision.amountChanged) {
            reviews.logCorrection(
                ClassificationCorrection(
                    expenseId = id,
                    voiceEntryId = voiceEntryId,
                    suggestedCategoryName = suggested,
                    finalCategoryName = chosen,
                    suggestedAmountCents = decision.draft.amountCents,
                    finalAmountCents = decision.amountCents,
                ),
            )
        }
    }

    private fun ReviewDecision.toReviewedExpense(): ReviewedExpense =
        ReviewedExpense(id = checkNotNull(draft.id), amountCents = amountCents, date = date, categoryId = categoryId)
}
