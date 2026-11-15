package app.nemla.ai.api

import kotlinx.datetime.LocalDate

enum class AudioEncoding { WAV_PCM16 }

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
    val languageTag: String,
)

data class Transcript(val text: String, val languageTag: String?)

data class CategoryHint(val id: String, val label: String)

data class ExpenseExtractionRequest(
    val text: String,
    val languageTag: String,
    val today: LocalDate,
    val categories: List<CategoryHint>,
)

data class ExpenseDraft(
    val remoteId: String?,
    val amountCents: Long,
    val label: String,
    val date: LocalDate,
    val categoryId: String?,
    val merchant: String? = null,
)

data class ExtractedExpenses(val drafts: List<ExpenseDraft>)

data class VoiceCapture(
    val entryId: String?,
    val transcript: Transcript,
    val drafts: List<ExpenseDraft>,
)

class RawStatement(
    val text: String,
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

data class PeriodReportRequest(val period: String, val kind: ReviewPeriodKind)

data class CategoryReview(
    val name: String,
    val amountCents: Long,
    val share: Double,
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
