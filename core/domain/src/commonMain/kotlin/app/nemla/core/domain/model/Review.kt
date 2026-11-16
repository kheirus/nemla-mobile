package app.nemla.core.domain.model

import kotlinx.datetime.LocalDate

class ReviewedExpense(
    val id: ExpenseId,
    val amountCents: Long,
    val date: LocalDate,
    val categoryId: CategoryId?,
) {
    override fun equals(other: Any?): Boolean =
        other is ReviewedExpense &&
            other.id == id &&
            other.amountCents == amountCents &&
            other.date == date &&
            other.categoryId == categoryId

    override fun hashCode(): Int = listOf(id, amountCents, date, categoryId).hashCode()

    override fun toString(): String = "ReviewedExpense(id=${id.value})"
}

class CategoryOutcome(
    val expenseId: ExpenseId,
    val merchantKey: String,
    val categoryId: CategoryId?,
    val suggestedCategoryName: String?,
    val chosenCategoryName: String?,
    val wasCorrection: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        other is CategoryOutcome &&
            other.expenseId == expenseId &&
            other.merchantKey == merchantKey &&
            other.categoryId == categoryId &&
            other.suggestedCategoryName == suggestedCategoryName &&
            other.chosenCategoryName == chosenCategoryName &&
            other.wasCorrection == wasCorrection

    override fun hashCode(): Int =
        listOf(expenseId, merchantKey, categoryId, suggestedCategoryName, chosenCategoryName, wasCorrection).hashCode()

    override fun toString(): String = "CategoryOutcome(expenseId=${expenseId.value}, wasCorrection=$wasCorrection)"
}

class ClassificationCorrection(
    val expenseId: ExpenseId,
    val voiceEntryId: VoiceEntryId?,
    val suggestedCategoryName: String?,
    val finalCategoryName: String?,
    val suggestedAmountCents: Long,
    val finalAmountCents: Long,
) {
    override fun toString(): String = "ClassificationCorrection(expenseId=${expenseId.value})"
}

class ReviewDecision(
    val draft: ExpenseDraft,
    val amountCents: Long,
    val date: LocalDate,
    val categoryId: CategoryId?,
) {
    val categoryChanged: Boolean get() = categoryId != draft.categoryId

    val amountChanged: Boolean get() = amountCents != draft.amountCents

    override fun toString(): String = "ReviewDecision(id=${draft.id?.value})"
}
