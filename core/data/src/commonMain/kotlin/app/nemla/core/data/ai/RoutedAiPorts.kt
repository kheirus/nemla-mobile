package app.nemla.core.data.ai

import app.nemla.ai.api.AiCapability
import app.nemla.ai.api.AiResult
import app.nemla.ai.api.AiRouter
import app.nemla.ai.api.BudgetSuggestionRequest
import app.nemla.ai.api.CategoryHint
import app.nemla.ai.api.ExecutionLocation
import app.nemla.ai.api.ExpenseExtractionRequest
import app.nemla.ai.api.PeriodReportRequest
import app.nemla.ai.api.RawStatement
import app.nemla.ai.api.RecurringParsingRequest
import app.nemla.ai.api.SpeechRequest
import app.nemla.ai.api.StatementParsingRequest
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.common.flatMap
import app.nemla.core.common.getOrNull
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.BudgetEstimate
import app.nemla.core.domain.model.PeriodReview
import app.nemla.core.domain.model.RecurringChargeDraft
import app.nemla.core.domain.model.ReviewPeriod
import app.nemla.core.domain.model.StatementFile
import app.nemla.core.domain.model.StatementImport
import app.nemla.core.domain.model.VoiceCapture
import app.nemla.core.domain.model.VoiceRecording
import app.nemla.core.domain.port.BudgetAdvisor
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.domain.port.PeriodReviewer
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.RecurringChargeParser
import app.nemla.core.domain.port.StatementImporter
import app.nemla.core.domain.port.VoiceExpenseCapturer
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

internal class RoutedVoiceExpenseCapturer(
    private val router: AiRouter,
    private val categories: CategoryRepository,
    private val clock: Clock,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : VoiceExpenseCapturer {
    override suspend fun capture(
        recording: VoiceRecording,
        language: AppLanguage,
    ): AppResult<VoiceCapture> {
        val request = SpeechRequest(recording.toAudioClip(), language.tag)
        return if (canTranscribeOnDevice()) {
            captureInTwoSteps(request, language)
        } else {
            router.runForApp(AiCapability.VoiceExpenseCapture, request).flatMap { mapResponse { it.toDomain() } }
        }
    }

    private fun canTranscribeOnDevice(): Boolean =
        router.enginesFor(AiCapability.SpeechToText).any { it.location == ExecutionLocation.ON_DEVICE } &&
            router.enginesFor(AiCapability.ExpenseExtraction).isNotEmpty()

    private suspend fun captureInTwoSteps(
        request: SpeechRequest,
        language: AppLanguage,
    ): AppResult<VoiceCapture> =
        router.runForApp(AiCapability.SpeechToText, request).flatMap { transcript ->
            categories.list().flatMap { owned ->
                val extraction =
                    ExpenseExtractionRequest(
                        text = transcript.text,
                        languageTag = language.tag,
                        today = clock.todayIn(timeZone()),
                        categories = owned.map { CategoryHint(it.id.value, it.name) },
                    )
                router.runForApp(AiCapability.ExpenseExtraction, extraction).flatMap { extracted ->
                    mapResponse { VoiceCapture(null, transcript.text, extracted.drafts.map { it.toDomain() }) }
                }
            }
        }
}

internal class RoutedStatementImporter(
    private val router: AiRouter,
    private val extractor: StatementTextExtractor,
    private val categories: CategoryRepository,
    private val profiles: ProfileRepository,
) : StatementImporter {
    override suspend fun import(
        file: StatementFile,
        language: AppLanguage,
    ): AppResult<StatementImport> {
        if (router.enginesFor(AiCapability.StatementParsing).isEmpty()) return AppResult.Failure(AppError.NotSupportedYet)
        return extractor.extract(file).flatMap { text ->
            if (readableLength(text) < MIN_READABLE_CHARS) {
                AppResult.Failure(AppError.Validation(ValidationCode.UNREADABLE_FILE))
            } else {
                anonymizeThenParse(text, language)
            }
        }
    }

    private suspend fun anonymizeThenParse(
        text: String,
        language: AppLanguage,
    ): AppResult<StatementImport> {
        val knownNames = listOfNotNull(profiles.getProfile().getOrNull()?.pseudo)
        val anonymized =
            when (val result = router.run(AiCapability.StatementAnonymization, RawStatement(text, knownNames))) {
                is AiResult.Failure -> {
                    return AppResult.Failure(result.error.toAppError())
                }

                is AiResult.Success -> {
                    if (result.trace.servedBy?.location != ExecutionLocation.ON_DEVICE) return AppResult.Failure(AppError.Forbidden)
                    result.value
                }
            }
        return categories.list().flatMap { owned ->
            val request = StatementParsingRequest(anonymized, language.tag, owned.map { CategoryHint(it.id.value, it.name) })
            router.runForApp(AiCapability.StatementParsing, request).flatMap { parsed ->
                mapResponse { StatementImport(parsed.drafts.map { it.toDomain() }, anonymized.report.total) }
            }
        }
    }

    private fun readableLength(text: String): Int =
        text
            .replace("\r", "")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .length

    private companion object {
        const val MIN_READABLE_CHARS = 20
    }
}

internal class RoutedRecurringChargeParser(
    private val router: AiRouter,
) : RecurringChargeParser {
    override suspend fun parse(
        text: String,
        language: AppLanguage,
    ): AppResult<List<RecurringChargeDraft>> =
        router.runForApp(AiCapability.RecurringParsing, RecurringParsingRequest(text, language.tag)).flatMap { parsed ->
            mapResponse { parsed.charges.map { it.toDomain() } }
        }
}

internal class RoutedBudgetAdvisor(
    private val router: AiRouter,
) : BudgetAdvisor {
    override suspend fun suggest(): AppResult<BudgetEstimate> =
        router.runForApp(AiCapability.BudgetSuggestion, BudgetSuggestionRequest).flatMap { mapResponse { it.toDomain() } }
}

internal class RoutedPeriodReviewer(
    private val router: AiRouter,
) : PeriodReviewer {
    override suspend fun review(period: ReviewPeriod): AppResult<PeriodReview> {
        val (value, kind) = period.toRequestPeriod()
        return router.runForApp(AiCapability.PeriodReport, PeriodReportRequest(value, kind)).flatMap { mapResponse { it.toDomain() } }
    }
}
