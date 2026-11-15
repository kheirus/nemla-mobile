package app.nemla.core.domain.model

import kotlinx.datetime.LocalDate

enum class CategorySource { RULE, CORPUS, AI }

class ExpenseDraft(
    val id: ExpenseId?,
    val amountCents: Long,
    val label: String,
    val date: LocalDate,
    val categoryId: CategoryId?,
    val categorySource: CategorySource?,
) {
    override fun equals(other: Any?): Boolean =
        other is ExpenseDraft &&
            other.id == id &&
            other.amountCents == amountCents &&
            other.label == label &&
            other.date == date &&
            other.categoryId == categoryId &&
            other.categorySource == categorySource

    override fun hashCode(): Int = listOf(id, amountCents, label, date, categoryId, categorySource).hashCode()

    override fun toString(): String = "ExpenseDraft(id=${id?.value}, label=***)"
}

class VoiceRecording(
    val wavBytes: ByteArray,
    val sampleRateHz: Int,
    val durationMillis: Long,
) {
    override fun toString(): String = "VoiceRecording(${wavBytes.size} bytes, ${durationMillis}ms)"
}

class VoiceCapture(
    val entryId: VoiceEntryId?,
    val transcript: String,
    val drafts: List<ExpenseDraft>,
) {
    override fun equals(other: Any?): Boolean =
        other is VoiceCapture && other.entryId == entryId && other.transcript == transcript && other.drafts == drafts

    override fun hashCode(): Int = listOf(entryId, transcript, drafts).hashCode()

    override fun toString(): String = "VoiceCapture(entryId=${entryId?.value}, drafts=${drafts.size})"
}

enum class ChargeFrequency { MONTHLY, YEARLY }

class RecurringChargeDraft(
    val name: String,
    val amountCents: Long,
    val frequency: ChargeFrequency,
    val dayOfMonth: Int,
    val categoryId: CategoryId?,
) {
    override fun equals(other: Any?): Boolean =
        other is RecurringChargeDraft &&
            other.name == name &&
            other.amountCents == amountCents &&
            other.frequency == frequency &&
            other.dayOfMonth == dayOfMonth &&
            other.categoryId == categoryId

    override fun hashCode(): Int = listOf(name, amountCents, frequency, dayOfMonth, categoryId).hashCode()

    override fun toString(): String = "RecurringChargeDraft(name=***, frequency=$frequency)"
}

enum class StatementFormat { PDF, CSV, SPREADSHEET, TEXT }

class StatementFile(
    val fileName: String,
    val format: StatementFormat,
    val bytes: ByteArray,
) {
    override fun toString(): String = "StatementFile(format=$format, ${bytes.size} bytes)"
}

data class StatementImport(
    val drafts: List<ExpenseDraft>,
    val redactionCount: Int,
)
