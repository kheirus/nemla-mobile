package app.nemla.core.data

import app.nemla.ai.api.AiAttempt
import app.nemla.ai.api.AiCapability
import app.nemla.ai.api.AiEngine
import app.nemla.ai.api.AiError
import app.nemla.ai.api.AiException
import app.nemla.ai.api.AiRoutingContext
import app.nemla.ai.api.AiTrace
import app.nemla.ai.api.AnonymizedText
import app.nemla.ai.api.AttemptOutcome
import app.nemla.ai.api.BudgetEstimate
import app.nemla.ai.api.CategoryEstimate
import app.nemla.ai.api.CategoryReview
import app.nemla.ai.api.ChargeFrequency
import app.nemla.ai.api.DataSensitivity
import app.nemla.ai.api.EngineAvailability
import app.nemla.ai.api.EngineDescriptor
import app.nemla.ai.api.ExecutionLocation
import app.nemla.ai.api.ExpenseDraft
import app.nemla.ai.api.ExtractedExpenses
import app.nemla.ai.api.MonthTotal
import app.nemla.ai.api.ParsedRecurringCharges
import app.nemla.ai.api.ParsedStatement
import app.nemla.ai.api.PeriodReview
import app.nemla.ai.api.RecurringChargeDraft
import app.nemla.ai.api.ReviewPeriodKind
import app.nemla.ai.api.StatementParsingRequest
import app.nemla.ai.api.Transcript
import app.nemla.ai.api.VoiceCapture
import app.nemla.ai.local.RuleBasedStatementAnonymizer
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.NoOpLogger
import app.nemla.core.common.ValidationCode
import app.nemla.core.common.getOrNull
import app.nemla.core.data.ai.ConnectivityMonitor
import app.nemla.core.data.ai.LoggingAiTraceListener
import app.nemla.core.data.ai.RepositoryAiContextSource
import app.nemla.core.data.ai.RoutedBudgetAdvisor
import app.nemla.core.data.ai.RoutedPeriodReviewer
import app.nemla.core.data.ai.RoutedRecurringChargeParser
import app.nemla.core.data.ai.RoutedStatementImporter
import app.nemla.core.data.ai.RoutedVoiceExpenseCapturer
import app.nemla.core.data.ai.toAppError
import app.nemla.core.data.consent.SecureStoreAiConsentRepository
import app.nemla.core.domain.model.AiConsentLevel
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.CategoryShare
import app.nemla.core.domain.model.EstimateBasis
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.MonthSpending
import app.nemla.core.domain.model.ReviewPeriod
import app.nemla.core.domain.model.StatementFormat
import app.nemla.core.domain.model.StatementText
import app.nemla.core.domain.model.VoiceEntryId
import app.nemla.core.domain.model.VoiceRecording
import app.nemla.core.security.InMemorySecureStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import app.nemla.core.domain.model.ChargeFrequency as DomainChargeFrequency

class AiGatewayTest {
    private val recording = VoiceRecording(ByteArray(64), 16_000, 2_000)
    private val draft = ExpenseDraft("exp-1", 4_200, "Marché", LocalDate(2026, 11, 15), "cat-1", "marche")
    private val capture = VoiceCapture("entry-1", Transcript("marché 42 euros", "fr"), listOf(draft))

    private fun statement(text: String): StatementText = StatementText(text, StatementFormat.CSV)

    private val statementText =
        "BANQUE EXEMPLE\nM. CAMILLE MARTIN\nRelevé de compte du 1 mars 2026 au 31 mars 2026\nDate Libellé Débit Crédit\n" +
            "02/03/2026 CB CARREFOUR MARKET 02/03 45,20\n05/03/2026 VIR SEPA FR76 3000 6000 0112 3456 7890 189 MARTIN 120,00\n" +
            "06/03/2026 REMB camille 12,00"

    @Test
    fun contextSourceCombinesConnectivityCreditsAndConsents() =
        runTest {
            val consents = SecureStoreAiConsentRepository(InMemorySecureStore(), FixedClock(), NoOpLogger)
            val quotas = FakeQuotaRepository()
            var online = true
            val connectivity =
                object : ConnectivityMonitor {
                    override suspend fun isOnline(): Boolean = online
                }
            val source = RepositoryAiContextSource(connectivity, quotas, consents)
            assertEquals(AiRoutingContext(online = true, remainingCredits = 2, remoteConsent = emptySet()), source.current())

            consents.grant(AiConsentLevel.ANONYMIZED)
            assertEquals(setOf(DataSensitivity.ANONYMIZED), source.current().remoteConsent)
            consents.grant(AiConsentLevel.PERSONAL)
            assertEquals(setOf(DataSensitivity.ANONYMIZED, DataSensitivity.PERSONAL), source.current().remoteConsent)
            consents.revoke(AiConsentLevel.ANONYMIZED)
            assertEquals(setOf(DataSensitivity.ANONYMIZED, DataSensitivity.PERSONAL), source.current().remoteConsent)

            quotas.result = AppResult.Failure(AppError.Network.Offline)
            assertNull(source.current().remainingCredits)

            online = false
            val callsBefore = quotas.calls
            assertEquals(false, source.current().online)
            assertEquals(callsBefore, quotas.calls)
        }

    @Test
    fun traceListenerLogsOnlyTechnicalFacts() {
        val logger = RecordingLogger()
        val listener = LoggingAiTraceListener(logger)
        val engine = EngineDescriptor("nemla-voice-capture", "nemla-backend", ExecutionLocation.REMOTE, 1)
        listener.onTrace(AiTrace("voice_expense_capture", listOf(AiAttempt(engine, AttemptOutcome.Succeeded))))
        listener.onTrace(AiTrace("recurring_parsing", listOf(AiAttempt(engine, AttemptOutcome.Failed(AiError.Rejected("Jean Dupont"))))))
        listener.onTrace(AiTrace("unknown", emptyList()))
        assertEquals(
            listOf(
                "INFO AI_REQUEST_SERVED kind=VOICE_EXPENSE_CAPTURE attempt=1 service=REMOTE",
                "WARN AI_REQUEST_FAILED kind=RECURRING_PARSING attempt=1 reason=REJECTED",
                "WARN AI_REQUEST_FAILED kind=OTHER attempt=0 reason=NO_ENGINE",
            ),
            logger.lines,
        )
    }

    @Test
    fun voiceUsesTheCombinedBackendEngineWithoutLocalTranscription() =
        runTest {
            val combined = FakeEngine(AiCapability.VoiceExpenseCapture, "voice", cost = 1) { capture }
            val remoteTranscription = FakeEngine(AiCapability.SpeechToText, "stt") { Transcript("x", "fr") }
            val categories = FakeCategoryRepository()
            val capturer = RoutedVoiceExpenseCapturer(router(combined, remoteTranscription), categories, FixedClock())
            val result = capturer.capture(recording, AppLanguage.EN).getOrNull()!!
            assertEquals(VoiceEntryId("entry-1"), result.entryId)
            assertEquals("marché 42 euros", result.transcript)
            val mapped = result.drafts.single()
            assertEquals(ExpenseId("exp-1"), mapped.id)
            assertEquals(4_200, mapped.amountCents)
            assertEquals(CategoryId("cat-1"), mapped.categoryId)
            assertNull(mapped.categorySource)
            assertEquals("en", combined.inputs.single().languageTag)
            assertTrue(remoteTranscription.inputs.isEmpty())
            assertEquals(0, categories.listCalls)
        }

    @Test
    fun voiceChainsLocalTranscriptionAndExtractionWhenAvailable() =
        runTest {
            val combined = FakeEngine(AiCapability.VoiceExpenseCapture, "voice", cost = 1) { capture }
            val localStt =
                FakeEngine(AiCapability.SpeechToText, "local-stt", ExecutionLocation.ON_DEVICE) { Transcript("pain 1,20", "fr") }
            val extraction =
                FakeEngine(AiCapability.ExpenseExtraction, "extract", ExecutionLocation.ON_DEVICE) {
                    ExtractedExpenses(listOf(draft.copy(remoteId = null, amountCents = 120, label = "Pain")))
                }
            val clock = FixedClock()
            val capturer =
                RoutedVoiceExpenseCapturer(router(combined, localStt, extraction), FakeCategoryRepository(), clock) { TimeZone.UTC }
            val result = capturer.capture(recording, AppLanguage.FR).getOrNull()!!
            assertNull(result.entryId)
            assertEquals("pain 1,20", result.transcript)
            assertEquals(120, result.drafts.single().amountCents)
            assertTrue(combined.inputs.isEmpty())
            val request = extraction.inputs.single()
            assertEquals("pain 1,20", request.text)
            assertEquals(LocalDate(2027, 1, 15), request.today)
            assertEquals(listOf("cat-1" to "Courses"), request.categories.map { it.id to it.label })
        }

    @Test
    fun voiceWithoutConsentFailsWithConsentRequired() =
        runTest {
            val combined = FakeEngine(AiCapability.VoiceExpenseCapture, "voice", cost = 1) { capture }
            val context = FixedContextSource(AiRoutingContext(true, 10, setOf(DataSensitivity.ANONYMIZED)))
            val capturer = RoutedVoiceExpenseCapturer(router(combined, context = context), FakeCategoryRepository(), FixedClock())
            assertEquals(AppError.ConsentRequired, capturer.capture(recording, AppLanguage.FR).failure)
            assertTrue(combined.inputs.isEmpty())
        }

    @Test
    fun voiceWithoutCreditsFailsWithQuotaExceeded() =
        runTest {
            val combined = FakeEngine(AiCapability.VoiceExpenseCapture, "voice", cost = 1) { capture }
            val context = FixedContextSource(AiRoutingContext(true, 0, fullConsent))
            val capturer = RoutedVoiceExpenseCapturer(router(combined, context = context), FakeCategoryRepository(), FixedClock())
            assertEquals(AppError.QuotaExceeded, capturer.capture(recording, AppLanguage.FR).failure)
        }

    @Test
    fun statementImportIsNotSupportedWithoutParsingEngine() =
        runTest {
            val anonymizer = RuleBasedStatementAnonymizer()
            val importer =
                RoutedStatementImporter(router(anonymizer), FakeCategoryRepository(), FakeProfileRepository())
            assertEquals(AppError.NotSupportedYet, importer.import(statement(statementText), AppLanguage.FR).failure)
        }

    @Test
    fun statementReachesTheRemoteParserOnlyAfterLocalAnonymization() =
        runTest {
            val parser =
                FakeEngine(AiCapability.StatementParsing, "remote-parser") {
                    ParsedStatement(listOf(draft.copy(remoteId = null, label = "Carrefour market", amountCents = 4_520)))
                }
            val importer =
                RoutedStatementImporter(
                    router(RuleBasedStatementAnonymizer(), parser),
                    FakeCategoryRepository(),
                    FakeProfileRepository(pseudo = "Camille"),
                )
            val imported = importer.import(statement(statementText), AppLanguage.EN).getOrNull()!!
            val sent: StatementParsingRequest = parser.inputs.single()
            val sentText: AnonymizedText = sent.statement
            assertFalse(sentText.value.contains("FR76"))
            assertFalse(sentText.value.contains("MARTIN", ignoreCase = true))
            assertFalse(sentText.value.contains("camille", ignoreCase = true))
            assertTrue(sentText.value.contains("CARREFOUR MARKET"))
            assertTrue(sentText.value.contains("45,20"))
            assertEquals("en", sent.languageTag)
            assertEquals(listOf("cat-1"), sent.categories.map { it.id })
            assertEquals(sentText.report.total, imported.redactionCount)
            assertTrue(imported.redactionCount >= 4)
            assertEquals(4_520, imported.drafts.single().amountCents)
        }

    @Test
    fun statementIsNeverSentWithoutAnAnonymizer() =
        runTest {
            val parser = FakeEngine(AiCapability.StatementParsing, "remote-parser") { ParsedStatement(emptyList()) }
            val importer =
                RoutedStatementImporter(router(parser), FakeCategoryRepository(), FakeProfileRepository())
            assertEquals(AppError.NotSupportedYet, importer.import(statement(statementText), AppLanguage.FR).failure)
            assertTrue(parser.inputs.isEmpty())
        }

    @Test
    fun statementNeedsAnonymizedConsent() =
        runTest {
            val parser = FakeEngine(AiCapability.StatementParsing, "remote-parser") { ParsedStatement(emptyList()) }
            val context = FixedContextSource(AiRoutingContext(true, 10, emptySet()))
            val importer =
                RoutedStatementImporter(
                    router(RuleBasedStatementAnonymizer(), parser, context = context),
                    FakeCategoryRepository(),
                    FakeProfileRepository(),
                )
            assertEquals(AppError.ConsentRequired, importer.import(statement(statementText), AppLanguage.FR).failure)
            assertTrue(parser.inputs.isEmpty())
        }

    @Test
    fun tooShortStatementsAreRejectedLocally() =
        runTest {
            val parser = FakeEngine(AiCapability.StatementParsing, "remote-parser") { ParsedStatement(emptyList()) }
            val importer =
                RoutedStatementImporter(
                    router(RuleBasedStatementAnonymizer(), parser),
                    FakeCategoryRepository(),
                    FakeProfileRepository(),
                )
            assertEquals(
                AppError.Validation(ValidationCode.UNREADABLE_FILE),
                importer.import(statement("  \n  court  \n"), AppLanguage.FR).failure,
            )
            assertTrue(parser.inputs.isEmpty())
        }

    private class ProbedParser(
        private val available: Boolean,
        private val behaviour: suspend (StatementParsingRequest) -> ParsedStatement,
    ) : AiEngine<StatementParsingRequest, ParsedStatement> {
        override val capability = AiCapability.StatementParsing
        override val descriptor = EngineDescriptor("remote-parser", "fake", ExecutionLocation.REMOTE, 0)
        val inputs = mutableListOf<StatementParsingRequest>()
        var probes = 0

        override suspend fun availability(): EngineAvailability {
            probes++
            return if (available) EngineAvailability.Ready else EngineAvailability.Unavailable("not_supported")
        }

        override suspend fun execute(input: StatementParsingRequest): ParsedStatement {
            inputs += input
            return behaviour(input)
        }
    }

    private fun probedImporter(parser: ProbedParser): RoutedStatementImporter =
        RoutedStatementImporter(router(RuleBasedStatementAnonymizer(), parser), FakeCategoryRepository(), FakeProfileRepository())

    @Test
    fun missingImportEndpointIsNotSupportedAndNothingIsSent() =
        runTest {
            val parser = ProbedParser(available = false) { ParsedStatement(emptyList()) }
            val redactions = mutableListOf<Int>()
            val result = probedImporter(parser).import(statement(statementText), AppLanguage.FR) { redactions += it }
            assertEquals(AppError.NotSupportedYet, result.failure)
            assertEquals(1, parser.probes)
            assertTrue(parser.inputs.isEmpty())
            assertEquals(1, redactions.size)
            assertTrue(redactions.single() >= 4)
        }

    @Test
    fun offlineProbeIsReportedAsOffline() =
        runTest {
            val parser =
                object : AiEngine<StatementParsingRequest, ParsedStatement> {
                    override val capability = AiCapability.StatementParsing
                    override val descriptor = EngineDescriptor("remote-parser", "fake", ExecutionLocation.REMOTE, 0)
                    var calls = 0

                    override suspend fun availability(): EngineAvailability = EngineAvailability.Unavailable("offline")

                    override suspend fun execute(input: StatementParsingRequest): ParsedStatement {
                        calls++
                        return ParsedStatement(emptyList())
                    }
                }
            val importer =
                RoutedStatementImporter(router(RuleBasedStatementAnonymizer(), parser), FakeCategoryRepository(), FakeProfileRepository())
            assertEquals(AppError.Network.Offline, importer.import(statement(statementText), AppLanguage.FR).failure)
            assertEquals(0, parser.calls)
        }

    @Test
    fun endpointDisappearingDuringTheCallIsNotSupported() =
        runTest {
            val parser = ProbedParser(available = true) { throw AiException(AiError.EngineFailure("not_supported")) }
            assertEquals(AppError.NotSupportedYet, probedImporter(parser).import(statement(statementText), AppLanguage.FR).failure)
        }

    @Test
    fun anonymizationIsReportedBeforeTheRemoteParserRuns() =
        runTest {
            val events = mutableListOf<String>()
            val parser =
                ProbedParser(available = true) {
                    events += "parse"
                    ParsedStatement(emptyList())
                }
            probedImporter(parser).import(statement(statementText), AppLanguage.FR) { events += "anonymized" }
            assertEquals(listOf("anonymized", "parse"), events)
        }

    @Test
    fun serverQuotaAndRejectionsAreTranslated() =
        runTest {
            val quota = ProbedParser(available = true) { throw AiException(AiError.QuotaExceeded) }
            assertEquals(AppError.QuotaExceeded, probedImporter(quota).import(statement(statementText), AppLanguage.FR).failure)
            val period = ProbedParser(available = true) { throw AiException(AiError.Rejected("PERIOD_TOO_LONG")) }
            assertEquals(
                AppError.Validation(ValidationCode.PERIOD_TOO_LONG),
                probedImporter(period).import(statement(statementText), AppLanguage.FR).failure,
            )
            val empty = ProbedParser(available = true) { throw AiException(AiError.Rejected("NO_EXPENSE_DETECTED")) }
            assertEquals(
                AppError.Validation(ValidationCode.NO_EXPENSE_DETECTED),
                probedImporter(empty).import(statement(statementText), AppLanguage.FR).failure,
            )
        }

    @Test
    fun recurringChargesAreMappedAndRejectionsTranslated() =
        runTest {
            val engine =
                FakeEngine(AiCapability.RecurringParsing, "recurring") {
                    ParsedRecurringCharges(listOf(RecurringChargeDraft("Netflix", 1_349, ChargeFrequency.YEARLY, 5, "cat-1")))
                }
            val parsed = RoutedRecurringChargeParser(router(engine)).parse("Netflix", AppLanguage.FR).getOrNull()!!.single()
            assertEquals("Netflix", parsed.name)
            assertEquals(DomainChargeFrequency.YEARLY, parsed.frequency)
            assertEquals(CategoryId("cat-1"), parsed.categoryId)

            val rejecting = FakeEngine(AiCapability.RecurringParsing, "recurring") { throw AiException(AiError.Rejected("NO_CATEGORY")) }
            assertEquals(
                AppError.Validation(ValidationCode.NO_CATEGORY),
                RoutedRecurringChargeParser(router(rejecting)).parse("Netflix", AppLanguage.FR).failure,
            )
        }

    @Test
    fun budgetEstimateIsMapped() =
        runTest {
            val estimate =
                BudgetEstimate(
                    123_000,
                    1_476_000,
                    84_550,
                    listOf(MonthTotal("2026-08", 123_456)),
                    listOf(CategoryEstimate("cat-1", 12_000, 3)),
                    "moyenne_3_mois",
                )
            val engine = FakeEngine(AiCapability.BudgetSuggestion, "budget") { estimate }
            val mapped = RoutedBudgetAdvisor(router(engine)).suggest().getOrNull()!!
            assertEquals(listOf(MonthSpending(YearMonth(2026, 8), 123_456)), mapped.months)
            assertEquals(EstimateBasis.Average(3), mapped.basis)
            assertEquals(CategoryId("cat-1"), mapped.perCategory.single().categoryId)

            val fixed = FakeEngine(AiCapability.BudgetSuggestion, "budget") { estimate.copy(basis = "charges_fixes") }
            assertEquals(EstimateBasis.FixedCharges, RoutedBudgetAdvisor(router(fixed)).suggest().getOrNull()!!.basis)

            val broken = FakeEngine(AiCapability.BudgetSuggestion, "budget") { estimate.copy(months = listOf(MonthTotal("août", 1))) }
            assertEquals(AppError.Server(null), RoutedBudgetAdvisor(router(broken)).suggest().failure)
        }

    @Test
    fun periodReviewRequestsAndMapsThePeriod() =
        runTest {
            val engine =
                FakeEngine(AiCapability.PeriodReport, "report") { request ->
                    PeriodReview(
                        request.period,
                        request.kind,
                        if (request.kind == ReviewPeriodKind.YEAR) "2024" else "2026-09",
                        123_450,
                        100_000,
                        150_000,
                        42,
                        listOf(CategoryReview("Courses", 40_025, 32.0, -12.5)),
                        listOf("h"),
                        listOf("a"),
                    )
                }
            val reviewer = RoutedPeriodReviewer(router(engine))
            val monthly = reviewer.review(ReviewPeriod.Month(YearMonth(2026, 10))).getOrNull()!!
            assertEquals("2026-10", engine.inputs.last().period)
            assertEquals(ReviewPeriod.Month(YearMonth(2026, 10)), monthly.period)
            assertEquals(ReviewPeriod.Month(YearMonth(2026, 9)), monthly.previousPeriod)
            assertEquals(listOf(CategoryShare("Courses", 40_025, 32, -1_250)), monthly.categories)
            val yearly = reviewer.review(ReviewPeriod.Year(2025)).getOrNull()!!
            assertEquals(ReviewPeriodKind.YEAR, engine.inputs.last().kind)
            assertEquals(ReviewPeriod.Year(2024), yearly.previousPeriod)
        }

    @Test
    fun missingEngineIsNotSupportedYet() =
        runTest {
            assertEquals(AppError.NotSupportedYet, RoutedBudgetAdvisor(router()).suggest().failure)
        }

    @Test
    fun aiErrorsMapToAppErrors() {
        assertEquals(AppError.NotSupportedYet, AiError.NoEngine.toAppError())
        assertEquals(AppError.ConsentRequired, AiError.ConsentRequired.toAppError())
        assertEquals(AppError.QuotaExceeded, AiError.QuotaExceeded.toAppError())
        assertEquals(AppError.Network.Offline, AiError.Offline.toAppError())
        assertEquals(AppError.Unauthorized, AiError.Unauthorized.toAppError())
        assertEquals(AppError.Forbidden, AiError.PrivacyViolation.toAppError())
        assertEquals(AppError.Forbidden, AiError.Rejected(null).toAppError())
        assertEquals(AppError.Validation(ValidationCode.NO_SPEECH_DETECTED), AiError.Rejected("NO_SPEECH_DETECTED").toAppError())
        assertEquals(AppError.Validation(ValidationCode.INVALID_INPUT), AiError.Rejected("texte libre du serveur").toAppError())
        assertEquals(AppError.Network.Timeout, AiError.EngineFailure("timeout").toAppError())
        assertEquals(AppError.RateLimited, AiError.EngineFailure("rate_limited").toAppError())
        assertEquals(AppError.Server(null), AiError.EngineFailure("server_500").toAppError())
    }
}
