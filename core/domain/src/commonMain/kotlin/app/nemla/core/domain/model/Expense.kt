package app.nemla.core.domain.model

import kotlinx.datetime.LocalDate
import kotlin.jvm.JvmInline
import kotlin.time.Instant

enum class PaymentMethod { CARD, TRANSFER, CASH, CHEQUE }

enum class ExpenseSource { VOICE, MANUAL }

@JvmInline
value class RecurringChargeId(
    val value: String,
)

class Expense(
    val id: ExpenseId,
    val categoryId: CategoryId?,
    val voiceEntryId: VoiceEntryId?,
    val recurringChargeId: RecurringChargeId?,
    val amountCents: Long,
    val date: LocalDate,
    val debitDate: LocalDate,
    val source: ExpenseSource,
    val paymentMethod: PaymentMethod,
    val confirmed: Boolean,
    val createdAt: Instant?,
    val label: String? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is Expense &&
            other.id == id &&
            other.categoryId == categoryId &&
            other.voiceEntryId == voiceEntryId &&
            other.recurringChargeId == recurringChargeId &&
            other.amountCents == amountCents &&
            other.date == date &&
            other.debitDate == debitDate &&
            other.source == source &&
            other.paymentMethod == paymentMethod &&
            other.confirmed == confirmed &&
            other.createdAt == createdAt &&
            other.label == label

    override fun hashCode(): Int =
        listOf(
            id,
            categoryId,
            voiceEntryId,
            recurringChargeId,
            amountCents,
            date,
            debitDate,
            source,
            paymentMethod,
            confirmed,
            createdAt,
            label,
        ).hashCode()

    override fun toString(): String = "Expense(id=${id.value}, confirmed=$confirmed, amount=***, label=***)"
}

data class ExpenseQuery(
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val dateEquals: LocalDate? = null,
    val confirmed: Boolean? = null,
    val limit: Int? = null,
)

class ExpenseEdit(
    val amountCents: Long,
    val date: LocalDate,
    val categoryId: CategoryId?,
    val paymentMethod: PaymentMethod,
) {
    override fun equals(other: Any?): Boolean =
        other is ExpenseEdit &&
            other.amountCents == amountCents &&
            other.date == date &&
            other.categoryId == categoryId &&
            other.paymentMethod == paymentMethod

    override fun hashCode(): Int = listOf(amountCents, date, categoryId, paymentMethod).hashCode()

    override fun toString(): String = "ExpenseEdit(amount=***, paymentMethod=$paymentMethod)"
}

class RecurringCharge(
    val id: RecurringChargeId,
    val name: String,
    val amountCents: Long,
    val frequency: ChargeFrequency,
    val dayOfMonth: Int,
    val categoryId: CategoryId?,
) {
    override fun equals(other: Any?): Boolean =
        other is RecurringCharge &&
            other.id == id &&
            other.name == name &&
            other.amountCents == amountCents &&
            other.frequency == frequency &&
            other.dayOfMonth == dayOfMonth &&
            other.categoryId == categoryId

    override fun hashCode(): Int = listOf(id, name, amountCents, frequency, dayOfMonth, categoryId).hashCode()

    override fun toString(): String = "RecurringCharge(id=${id.value}, frequency=$frequency, name=***)"
}

enum class ReviewOrigin { VOICE, IMPORT }

class PendingReview(
    val origin: ReviewOrigin,
    val voiceEntryId: VoiceEntryId?,
    val drafts: List<ExpenseDraft>,
) {
    override fun equals(other: Any?): Boolean =
        other is PendingReview && other.origin == origin && other.voiceEntryId == voiceEntryId && other.drafts == drafts

    override fun hashCode(): Int = listOf(origin, voiceEntryId, drafts).hashCode()

    override fun toString(): String = "PendingReview(origin=$origin, drafts=${drafts.size})"
}
