package app.nemla.ai.remote.nemla

import app.nemla.ai.api.AiCapability
import app.nemla.ai.api.AiEngine
import app.nemla.ai.api.AiError
import app.nemla.ai.api.AiException
import app.nemla.ai.api.BudgetEstimate
import app.nemla.ai.api.BudgetSuggestionRequest
import app.nemla.ai.api.EngineAvailability
import app.nemla.ai.api.EngineDescriptor
import app.nemla.ai.api.ExecutionLocation
import app.nemla.ai.api.ParsedRecurringCharges
import app.nemla.ai.api.ParsedStatement
import app.nemla.ai.api.PeriodReportRequest
import app.nemla.ai.api.PeriodReview
import app.nemla.ai.api.RecurringParsingRequest
import app.nemla.ai.api.ReviewPeriodKind
import app.nemla.ai.api.SpeechRequest
import app.nemla.ai.api.StatementParsingRequest
import app.nemla.ai.api.Transcript
import app.nemla.ai.api.VoiceCapture
import app.nemla.core.common.ValidationCode
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

internal const val NEMLA_PROVIDER = "nemla-backend"

private fun remoteDescriptor(
    id: String,
    creditCost: Int,
): EngineDescriptor = EngineDescriptor(id, NEMLA_PROVIDER, ExecutionLocation.REMOTE, creditCost)

internal class NemlaVoiceCaptureEngine(
    private val api: NemlaApi,
) : AiEngine<SpeechRequest, VoiceCapture> {
    override val capability: AiCapability<SpeechRequest, VoiceCapture> = AiCapability.VoiceExpenseCapture
    override val descriptor: EngineDescriptor = remoteDescriptor("nemla-voice-capture", creditCost = 1)

    override suspend fun execute(input: SpeechRequest): VoiceCapture {
        val lang = backendLanguage(input.languageTag)
        return api.processVoice(input.audio.bytes, lang).valueOrThrow().toVoiceCapture(lang)
    }
}

internal class NemlaTranscriptionEngine(
    private val api: NemlaApi,
) : AiEngine<SpeechRequest, Transcript> {
    override val capability: AiCapability<SpeechRequest, Transcript> = AiCapability.SpeechToText
    override val descriptor: EngineDescriptor = remoteDescriptor("nemla-transcription", creditCost = 0)

    override suspend fun execute(input: SpeechRequest): Transcript {
        val lang = backendLanguage(input.languageTag)
        return api.transcribe(input.audio.bytes, lang).valueOrThrow().toTranscript(lang)
    }
}

internal class NemlaRecurringParsingEngine(
    private val api: NemlaApi,
) : AiEngine<RecurringParsingRequest, ParsedRecurringCharges> {
    override val capability: AiCapability<RecurringParsingRequest, ParsedRecurringCharges> = AiCapability.RecurringParsing
    override val descriptor: EngineDescriptor = remoteDescriptor("nemla-recurring-parsing", creditCost = 0)

    override suspend fun execute(input: RecurringParsingRequest): ParsedRecurringCharges {
        val text = input.text.trim()
        if (text.length !in MIN_TEXT_LENGTH..MAX_TEXT_LENGTH) {
            throw AiException(AiError.Rejected(ValidationCode.INVALID_INPUT.name))
        }
        return api.parseRecurring(text, backendLanguage(input.languageTag)).valueOrThrow().toCharges()
    }

    private companion object {
        const val MIN_TEXT_LENGTH = 2
        const val MAX_TEXT_LENGTH = 4_000
    }
}

internal class NemlaBudgetSuggestionEngine(
    private val api: NemlaApi,
) : AiEngine<BudgetSuggestionRequest, BudgetEstimate> {
    override val capability: AiCapability<BudgetSuggestionRequest, BudgetEstimate> = AiCapability.BudgetSuggestion
    override val descriptor: EngineDescriptor = remoteDescriptor("nemla-budget-suggestion", creditCost = 0)

    override suspend fun execute(input: BudgetSuggestionRequest): BudgetEstimate = api.suggestBudget().valueOrThrow().toEstimate()
}

internal class NemlaPeriodReportEngine(
    private val api: NemlaApi,
) : AiEngine<PeriodReportRequest, PeriodReview> {
    override val capability: AiCapability<PeriodReportRequest, PeriodReview> = AiCapability.PeriodReport
    override val descriptor: EngineDescriptor = remoteDescriptor("nemla-period-report", creditCost = 0)

    override suspend fun execute(input: PeriodReportRequest): PeriodReview {
        val period = input.period.trim()
        val valid =
            when (input.kind) {
                ReviewPeriodKind.MONTH -> MONTH_PATTERN.matches(period)
                ReviewPeriodKind.YEAR -> YEAR_PATTERN.matches(period)
            }
        if (!valid) throw AiException(AiError.Rejected(ValidationCode.INVALID_INPUT.name))
        val parameter = if (input.kind == ReviewPeriodKind.YEAR) "year" else "month"
        return api.periodReport(parameter, period).valueOrThrow().toReview(period, input.kind)
    }

    private companion object {
        val MONTH_PATTERN = Regex("^[0-9]{4}-(0[1-9]|1[0-2])$")
        val YEAR_PATTERN = Regex("^[0-9]{4}$")
    }
}

internal class NemlaStatementParsingEngine(
    private val api: NemlaApi,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : AiEngine<StatementParsingRequest, ParsedStatement> {
    override val capability: AiCapability<StatementParsingRequest, ParsedStatement> = AiCapability.StatementParsing
    override val descriptor: EngineDescriptor = remoteDescriptor("nemla-statement-parsing", creditCost = 0)

    override suspend fun availability(): EngineAvailability =
        when (val probe = api.probeImportText()) {
            ImportTextProbe.Available -> EngineAvailability.Ready
            ImportTextProbe.Missing -> EngineAvailability.Unavailable(REASON_NOT_SUPPORTED)
            ImportTextProbe.Offline -> EngineAvailability.Unavailable(REASON_OFFLINE)
            ImportTextProbe.Timeout -> EngineAvailability.Unavailable(REASON_TIMEOUT)
            is ImportTextProbe.Failed -> EngineAvailability.Unavailable(probe.status?.let { "server_$it" } ?: "server")
        }

    override suspend fun execute(input: StatementParsingRequest): ParsedStatement {
        val text = input.statement.value
        if (text.trim().length < MIN_TEXT_LENGTH) throw AiException(AiError.Rejected(ValidationCode.UNREADABLE_FILE.name))
        val body =
            ImportTextRequestDto(
                text = text,
                lang = backendLanguage(input.languageTag),
                today = clock.todayIn(timeZone()).toString(),
            )
        return api.importText(body).valueOrThrow().toParsedStatement()
    }

    private companion object {
        const val MIN_TEXT_LENGTH = 20
    }
}
