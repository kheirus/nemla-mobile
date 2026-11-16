package app.nemla.core.data.ai

import app.nemla.ai.api.AiCapability
import app.nemla.ai.api.AiError
import app.nemla.ai.api.AiResult
import app.nemla.ai.api.AiRouter
import app.nemla.ai.api.AudioClip
import app.nemla.ai.api.AudioEncoding
import app.nemla.ai.api.ReviewPeriodKind
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.domain.model.BudgetEstimate
import app.nemla.core.domain.model.CategoryEstimate
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.CategoryShare
import app.nemla.core.domain.model.EstimateBasis
import app.nemla.core.domain.model.ExpenseDraft
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.MonthSpending
import app.nemla.core.domain.model.PeriodReview
import app.nemla.core.domain.model.RecurringChargeDraft
import app.nemla.core.domain.model.ReviewPeriod
import app.nemla.core.domain.model.VoiceCapture
import app.nemla.core.domain.model.VoiceEntryId
import app.nemla.core.domain.model.VoiceRecording
import kotlinx.datetime.YearMonth
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import app.nemla.ai.api.BudgetEstimate as AiBudgetEstimate
import app.nemla.ai.api.ChargeFrequency as AiChargeFrequency
import app.nemla.ai.api.ExpenseDraft as AiExpenseDraft
import app.nemla.ai.api.PeriodReview as AiPeriodReview
import app.nemla.ai.api.RecurringChargeDraft as AiRecurringChargeDraft
import app.nemla.ai.api.VoiceCapture as AiVoiceCapture
import app.nemla.core.domain.model.ChargeFrequency as DomainChargeFrequency

internal const val FAILURE_TIMEOUT = "timeout"
internal const val FAILURE_RATE_LIMITED = "rate_limited"
internal const val FAILURE_NOT_SUPPORTED = "not_supported"
internal const val FAILURE_OFFLINE = "offline"

internal fun AiError.toAppError(): AppError =
    when (this) {
        AiError.NoEngine -> {
            AppError.NotSupportedYet
        }

        AiError.ConsentRequired -> {
            AppError.ConsentRequired
        }

        AiError.QuotaExceeded -> {
            AppError.QuotaExceeded
        }

        AiError.Offline -> {
            AppError.Network.Offline
        }

        AiError.Unauthorized -> {
            AppError.Unauthorized
        }

        AiError.PrivacyViolation -> {
            AppError.Forbidden
        }

        is AiError.Rejected -> {
            rejectionError(userMessage)
        }

        is AiError.EngineFailure -> {
            when (reason) {
                FAILURE_TIMEOUT -> AppError.Network.Timeout
                FAILURE_RATE_LIMITED -> AppError.RateLimited
                FAILURE_NOT_SUPPORTED -> AppError.NotSupportedYet
                FAILURE_OFFLINE -> AppError.Network.Offline
                else -> AppError.Server(null)
            }
        }
    }

private fun rejectionError(code: String?): AppError {
    if (code == null) return AppError.Forbidden
    val validation = ValidationCode.entries.firstOrNull { it.name == code } ?: ValidationCode.INVALID_INPUT
    return AppError.Validation(validation)
}

internal suspend fun <I : Any, O : Any> AiRouter.runForApp(
    capability: AiCapability<I, O>,
    input: I,
): AppResult<O> =
    when (val result = run(capability, input)) {
        is AiResult.Success -> AppResult.Success(result.value)
        is AiResult.Failure -> AppResult.Failure(result.error.toAppError())
    }

internal inline fun <T> mapResponse(block: () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: IllegalArgumentException) {
        AppResult.Failure(AppError.Server(null))
    }

internal fun VoiceRecording.toAudioClip(): AudioClip = AudioClip(wavBytes, AudioEncoding.WAV_PCM16, sampleRateHz, durationMillis)

internal fun AiExpenseDraft.toDomain(): ExpenseDraft =
    ExpenseDraft(
        id = remoteId?.let(::ExpenseId),
        amountCents = amountCents,
        label = label,
        date = date,
        categoryId = categoryId?.let(::CategoryId),
        categorySource = null,
    )

internal fun AiVoiceCapture.toDomain(): VoiceCapture =
    VoiceCapture(
        entryId = entryId?.let(::VoiceEntryId),
        transcript = transcript.text,
        drafts = drafts.map { it.toDomain() },
    )

internal fun AiRecurringChargeDraft.toDomain(): RecurringChargeDraft =
    RecurringChargeDraft(
        name = name,
        amountCents = amountCents,
        frequency =
            when (frequency) {
                AiChargeFrequency.MONTHLY -> DomainChargeFrequency.MONTHLY
                AiChargeFrequency.YEARLY -> DomainChargeFrequency.YEARLY
            },
        dayOfMonth = dayOfMonth,
        categoryId = categoryId?.let(::CategoryId),
    )

private val averageBasis = Regex("^moyenne_([0-9]+)_mois$")

internal fun AiBudgetEstimate.toDomain(): BudgetEstimate =
    BudgetEstimate(
        monthlyCents = monthlyCents,
        annualCents = annualCents,
        fixedMonthlyCents = fixedMonthlyCents,
        months = months.map { MonthSpending(YearMonth.parse(it.month), it.totalCents) },
        perCategory = perCategory.map { CategoryEstimate(CategoryId(it.categoryId), it.monthlyCents, it.monthsObserved) },
        basis =
            when {
                basis == "charges_fixes" -> {
                    EstimateBasis.FixedCharges
                }

                else -> {
                    EstimateBasis.Average(
                        averageBasis
                            .matchEntire(basis)
                            ?.groupValues
                            ?.get(1)
                            ?.toIntOrNull() ?: months.size,
                    )
                }
            },
    )

internal fun ReviewPeriod.toRequestPeriod(): Pair<String, ReviewPeriodKind> =
    when (this) {
        is ReviewPeriod.Month -> month.toString() to ReviewPeriodKind.MONTH
        is ReviewPeriod.Year -> year.toString().padStart(4, '0') to ReviewPeriodKind.YEAR
    }

internal fun parseReviewPeriod(
    value: String,
    kind: ReviewPeriodKind,
): ReviewPeriod =
    when (kind) {
        ReviewPeriodKind.MONTH -> ReviewPeriod.Month(YearMonth.parse(value))
        ReviewPeriodKind.YEAR -> ReviewPeriod.Year(value.toIntOrNull() ?: throw IllegalArgumentException("year"))
    }

internal fun AiPeriodReview.toDomain(): PeriodReview =
    PeriodReview(
        period = parseReviewPeriod(period, kind),
        previousPeriod = parseReviewPeriod(previousPeriod, kind),
        totalCents = totalCents,
        previousTotalCents = previousTotalCents,
        budgetCents = budgetCents,
        expenseCount = expenseCount,
        categories =
            categories.map {
                CategoryShare(
                    name = it.name,
                    amountCents = it.amountCents,
                    sharePercent = it.share.roundToInt(),
                    variationCents = (it.variation * 100).roundToLong(),
                )
            },
        highlights = highlights,
        advice = advice,
    )
