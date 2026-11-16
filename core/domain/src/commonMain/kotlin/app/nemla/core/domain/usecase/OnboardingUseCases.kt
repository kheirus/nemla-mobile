package app.nemla.core.domain.usecase

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.Category
import app.nemla.core.domain.model.ExpenseQuery
import app.nemla.core.domain.model.RecurringChargeDraft
import app.nemla.core.domain.model.RecurringChargeId
import app.nemla.core.domain.model.StartDestination
import app.nemla.core.domain.model.VoiceRecording
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.domain.port.ExpenseRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.RecurringChargeParser
import app.nemla.core.domain.port.RecurringChargeRepository
import app.nemla.core.domain.port.SpeechTranscriber

class ResolveStartDestination(
    private val profiles: ProfileRepository,
    private val charges: RecurringChargeRepository,
    private val expenses: ExpenseRepository,
) {
    suspend operator fun invoke(): StartDestination {
        profiles.bootstrap()
        val profile = profiles.getProfile()
        if (profile !is AppResult.Success) return StartDestination.DASHBOARD
        if (profile.value.onboardingDone || profile.value.budget.monthlyCents != 0L) return StartDestination.DASHBOARD
        val existingCharges = charges.list()
        if (existingCharges !is AppResult.Success || existingCharges.value.isNotEmpty()) return StartDestination.DASHBOARD
        val existingExpenses = expenses.list(ExpenseQuery(limit = 1))
        if (existingExpenses !is AppResult.Success || existingExpenses.value.isNotEmpty()) return StartDestination.DASHBOARD
        return StartDestination.ONBOARDING
    }
}

class PrepareOnboarding(
    private val profiles: ProfileRepository,
    private val categories: CategoryRepository,
) {
    suspend operator fun invoke(): AppResult<List<Category>> {
        profiles.bootstrap()
        return when (val result = categories.list()) {
            is AppResult.Success -> AppResult.Success(result.value.sortedBy { it.name.lowercase() })
            is AppResult.Failure -> result
        }
    }
}

class TranscribeSpeech(
    private val transcriber: SpeechTranscriber,
) {
    suspend operator fun invoke(
        recording: VoiceRecording,
        language: AppLanguage,
    ): AppResult<String> =
        when (val result = transcriber.transcribe(recording, language)) {
            is AppResult.Success -> {
                val text = result.value.trim()
                if (text.isEmpty()) AppResult.Failure(AppError.Validation(ValidationCode.NO_SPEECH_DETECTED)) else AppResult.Success(text)
            }

            is AppResult.Failure -> {
                result
            }
        }
}

class ParseRecurringCharges(
    private val parser: RecurringChargeParser,
) {
    suspend operator fun invoke(
        text: String,
        language: AppLanguage,
    ): AppResult<List<RecurringChargeDraft>> {
        val trimmed = text.trim()
        if (!canAnalyze(trimmed)) return AppResult.Failure(AppError.Validation(ValidationCode.INVALID_INPUT))
        return parser.parse(trimmed.take(MAX_LENGTH), language)
    }

    companion object {
        const val MIN_LENGTH: Int = 3
        const val MAX_LENGTH: Int = 4_000

        fun canAnalyze(text: String): Boolean = text.trim().length >= MIN_LENGTH
    }
}

class SaveRecurringCharges(
    private val charges: RecurringChargeRepository,
) {
    suspend operator fun invoke(drafts: List<RecurringChargeDraft>): AppResult<Unit> {
        if (drafts.isEmpty()) return AppResult.Success(Unit)
        return charges.addAll(drafts.map { it.normalized() })
    }

    companion object {
        const val DAY_MIN: Int = 1
        const val DAY_MAX: Int = 28

        fun isComplete(draft: RecurringChargeDraft): Boolean = draft.name.isNotBlank() && draft.amountCents > 0
    }
}

class DeleteRecurringCharge(
    private val charges: RecurringChargeRepository,
) {
    suspend operator fun invoke(id: RecurringChargeId): AppResult<Unit> = charges.delete(id)
}

class CompleteOnboarding(
    private val charges: RecurringChargeRepository,
    private val profiles: ProfileRepository,
) {
    suspend operator fun invoke(
        drafts: List<RecurringChargeDraft>,
        monthlyBudgetCents: Long?,
    ): AppResult<Unit> {
        if (drafts.isNotEmpty()) {
            val saved = charges.addAll(drafts.map { it.normalized() })
            if (saved is AppResult.Failure) return saved
        }
        return profiles.completeOnboarding(maxOf(0L, monthlyBudgetCents ?: 0L))
    }
}

private fun RecurringChargeDraft.normalized(): RecurringChargeDraft =
    RecurringChargeDraft(
        name = name.trim(),
        amountCents = maxOf(0L, amountCents),
        frequency = frequency,
        dayOfMonth = dayOfMonth.coerceIn(SaveRecurringCharges.DAY_MIN, SaveRecurringCharges.DAY_MAX),
        categoryId = categoryId,
    )
