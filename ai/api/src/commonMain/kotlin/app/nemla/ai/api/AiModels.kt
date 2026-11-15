package app.nemla.ai.api

import kotlinx.datetime.LocalDate

/*
 * Provider-neutral models exchanged with AI engines.
 * They belong to the AI boundary: the domain layer has its own entities and maps to and from these.
 * Amounts are integers in minor units (cents) to stay exact.
 */

// region Speech

enum class AudioEncoding { WAV_PCM16 }

/** A short recording held in memory. It is never written to shared storage. */
class AudioClip(
    val bytes: ByteArray,
    val encoding: AudioEncoding,
    val sampleRateHz: Int,
    val durationMillis: Long,
) {
    override fun toString(): String = "AudioClip(${bytes.size} bytes, ${durationMillis}ms)"
}

data class SpeechRequest(
    val audio: AudioClip,
    /** BCP 47 language tag of the user ("fr", "en"). */
    val languageTag: String,
)

data class Transcript(val text: String, val languageTag: String?)

// endregion

// region Expenses

/** A category the engine may assign. Only the id and label are shared, never user data. */
data class CategoryHint(val id: String, val label: String)

data class ExpenseExtractionRequest(
    val text: String,
    val languageTag: String,
    val today: LocalDate,
    val categories: List<CategoryHint>,
)

/** An expense proposed by an engine, to be reviewed by the user before it counts. */
data class ExpenseDraft(
    /** Set when the engine already stored the draft server-side (current nemla backend does). */
    val remoteId: String?,
    val amountCents: Long,
    val label: String,
    val date: LocalDate,
    val categoryId: String?,
    val merchant: String? = null,
)

data class ExtractedExpenses(val drafts: List<ExpenseDraft>)

data class VoiceCapture(
    /** Server-side id of the voice entry, when the engine keeps one. */
    val entryId: String?,
    val transcript: Transcript,
    val drafts: List<ExpenseDraft>,
)

// endregion

// region Bank statements

/** Text extracted on the device from a statement file (PDF, CSV, spreadsheet). */
class RawStatement(
    val text: String,
    /** Names the anonymiser must remove wherever they appear (account holder, if known). */
    val knownNames: List<String> = emptyList(),
) {
    override fun toString(): String = "RawStatement(length=${text.length})"
}

data class StatementParsingRequest(
    val statement: AnonymizedText,
    val languageTag: String,
    val categories: List<CategoryHint>,
)

data class ParsedStatement(val drafts: List<ExpenseDraft>)

// endregion

// region Recurring charges

data class RecurringParsingRequest(val text: String, val languageTag: String)

enum class ChargeFrequency { MONTHLY, YEARLY }

data class RecurringChargeDraft(
    val name: String,
    val amountCents: Long,
    val frequency: ChargeFrequency,
    val dayOfMonth: Int,
    val categoryId: String?,
)

data class ParsedRecurringCharges(val charges: List<RecurringChargeDraft>)

// endregion

// region Insights

data object BudgetSuggestionRequest

data class MonthTotal(val month: String, val totalCents: Long)

data class CategoryEstimate(val categoryId: String, val monthlyCents: Long, val monthsObserved: Int)

data class BudgetEstimate(
    val monthlyCents: Long,
    val annualCents: Long,
    val fixedMonthlyCents: Long,
    val months: List<MonthTotal>,
    val perCategory: List<CategoryEstimate>,
    val basis: String,
)

enum class ReviewPeriodKind { MONTH, YEAR }

/** [period] is "YYYY-MM" for a month, "YYYY" for a year. */
data class PeriodReportRequest(val period: String, val kind: ReviewPeriodKind)

data class CategoryReview(
    val name: String,
    val amountCents: Long,
    /** Share of the period total, 0..1. */
    val share: Double,
    /** Change against the previous period, as a ratio (0.12 = +12 %). */
    val variation: Double,
)

data class PeriodReview(
    val period: String,
    val kind: ReviewPeriodKind,
    val previousPeriod: String,
    val totalCents: Long,
    val previousTotalCents: Long,
    val budgetCents: Long,
    val expenseCount: Int,
    val categories: List<CategoryReview>,
    val highlights: List<String>,
    val advice: List<String>,
)

// endregion
