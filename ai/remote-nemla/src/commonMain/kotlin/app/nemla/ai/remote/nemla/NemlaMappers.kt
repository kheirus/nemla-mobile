package app.nemla.ai.remote.nemla

import app.nemla.ai.api.BudgetEstimate
import app.nemla.ai.api.CategoryEstimate
import app.nemla.ai.api.CategoryReview
import app.nemla.ai.api.ChargeFrequency
import app.nemla.ai.api.ExpenseDraft
import app.nemla.ai.api.MonthTotal
import app.nemla.ai.api.ParsedRecurringCharges
import app.nemla.ai.api.ParsedStatement
import app.nemla.ai.api.PeriodReview
import app.nemla.ai.api.RecurringChargeDraft
import app.nemla.ai.api.ReviewPeriodKind
import app.nemla.ai.api.Transcript
import app.nemla.ai.api.VoiceCapture
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val MIN_DEBIT_DAY = 1
private const val MAX_DEBIT_DAY = 28

internal fun JsonElement?.transcriptText(): String? =
    when (this) {
        is JsonObject -> (this["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        is JsonPrimitive -> takeIf { it.isString }?.content
        else -> null
    }

internal fun VoiceProcessDto.toVoiceCapture(lang: String): VoiceCapture =
    VoiceCapture(
        entryId = voiceEntryId,
        transcript = Transcript(transcript.transcriptText().orEmpty(), lang),
        drafts = expenses.mapIndexed { index, expense -> expense.toDraft(categorySources.getOrNull(index)?.merchantKey) },
    )

internal fun ExpenseDto.toDraft(merchant: String?): ExpenseDraft =
    ExpenseDraft(
        remoteId = id,
        amountCents = montant.toCents() ?: invalidResponse(),
        label = description.orEmpty(),
        date = date.toLocalDate() ?: invalidResponse(),
        categoryId = categoryId,
        merchant = merchant?.takeIf { it.isNotBlank() },
    )

internal fun ImportResponseDto.toParsedStatement(): ParsedStatement = ParsedStatement(expenses.map { it.toDraft(null) })

internal fun TranscribeDto.toTranscript(lang: String): Transcript = Transcript(transcript.transcriptText() ?: invalidResponse(), lang)

internal fun RecurringResponseDto.toCharges(): ParsedRecurringCharges =
    ParsedRecurringCharges(
        charges.map { charge ->
            RecurringChargeDraft(
                name = charge.nom?.trim()?.takeIf { it.isNotEmpty() } ?: invalidResponse(),
                amountCents = charge.montant.toCents() ?: invalidResponse(),
                frequency = if (charge.frequence == "annuelle") ChargeFrequency.YEARLY else ChargeFrequency.MONTHLY,
                dayOfMonth = charge.jourPrelevement.toDayOfMonth(),
                categoryId = charge.categoryId,
            )
        },
    )

internal fun BudgetSuggestionDto.toEstimate(): BudgetEstimate =
    BudgetEstimate(
        monthlyCents = monthly.toCents() ?: invalidResponse(),
        annualCents = annual.toCents() ?: invalidResponse(),
        fixedMonthlyCents = fixedMonthly.toCents() ?: 0,
        months = months.map { MonthTotal(it.month, it.total.toCents() ?: invalidResponse()) },
        perCategory =
            perCategory.mapNotNull { estimate ->
                val id = estimate.categoryId ?: return@mapNotNull null
                CategoryEstimate(id, estimate.monthly.toCents() ?: invalidResponse(), estimate.months)
            },
        basis = basis ?: invalidResponse(),
    )

internal fun PeriodReportDto.toReview(
    requestedPeriod: String,
    requestedKind: ReviewPeriodKind,
): PeriodReview =
    PeriodReview(
        period = month ?: requestedPeriod,
        kind =
            when (kind) {
                "annee" -> ReviewPeriodKind.YEAR
                "mois" -> ReviewPeriodKind.MONTH
                else -> requestedKind
            },
        previousPeriod = previousMonth ?: invalidResponse(),
        totalCents = total.toCents() ?: invalidResponse(),
        previousTotalCents = previousTotal.toCents() ?: 0,
        budgetCents = budget.toCents() ?: 0,
        expenseCount = expenseCount,
        categories =
            categories.map { category ->
                CategoryReview(
                    name = category.nom ?: invalidResponse(),
                    amountCents = category.montant.toCents() ?: invalidResponse(),
                    share = category.part.toDecimal() ?: 0.0,
                    variation = category.variation.toDecimal() ?: 0.0,
                )
            },
        highlights = highlights,
        advice = advice,
    )

private fun String?.toLocalDate(): LocalDate? =
    this?.let {
        try {
            LocalDate.parse(it.trim())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

private fun JsonElement?.toDayOfMonth(): Int {
    val day = toDecimal()?.let { kotlin.math.round(it).toInt() } ?: MIN_DEBIT_DAY
    return day.coerceIn(MIN_DEBIT_DAY, MAX_DEBIT_DAY)
}
