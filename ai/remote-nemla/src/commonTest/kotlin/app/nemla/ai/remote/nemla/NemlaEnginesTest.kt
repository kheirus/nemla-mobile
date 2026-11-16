package app.nemla.ai.remote.nemla

import app.nemla.ai.api.AiCapability
import app.nemla.ai.api.AiEngine
import app.nemla.ai.api.AiError
import app.nemla.ai.api.BudgetEstimate
import app.nemla.ai.api.BudgetSuggestionRequest
import app.nemla.ai.api.CategoryEstimate
import app.nemla.ai.api.CategoryReview
import app.nemla.ai.api.ChargeFrequency
import app.nemla.ai.api.ExecutionLocation
import app.nemla.ai.api.ExpenseDraft
import app.nemla.ai.api.MonthTotal
import app.nemla.ai.api.PeriodReportRequest
import app.nemla.ai.api.RecurringChargeDraft
import app.nemla.ai.api.RecurringParsingRequest
import app.nemla.ai.api.ReviewPeriodKind
import app.nemla.ai.api.Transcript
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NemlaEnginesTest {
    private val voiceResponse =
        """
        {"voice_entry_id":"entry-1","transcript":"marché 42 euros et pain 1,20",
         "expenses":[
           {"id":"exp-1","montant":42.0,"description":"Marché","date":"2026-11-15","category_id":"cat-1","source":"voice"},
           {"id":"exp-2","montant":1.2,"description":"Pain","date":"2026-11-14","category_id":null,"source":"voice"}],
         "category_sources":[{"merchant_key":"marche","source":"rule"},{"merchant_key":"pain","source":null}],
         "input_tokens":10,"output_tokens":5,"estimated_cost_eur":0.001}
        """.trimIndent()

    @Test
    fun voiceCaptureSendsMultipartAudioAndMapsExpenses() =
        runTest {
            val harness = RemoteHarness { json(voiceResponse) }
            val capture = NemlaVoiceCaptureEngine(harness.api).execute(speech("en-GB"))
            val request = harness.single
            assertEquals(HttpMethod.Post, request.data.method)
            assertEquals("https://api.example.com/api/public/voice/process", request.data.url.toString())
            assertEquals("Bearer token-1", request.data.headers[HttpHeaders.Authorization])
            assertTrue(
                request.data.body.contentType
                    .toString()
                    .startsWith("multipart/form-data"),
            )
            assertTrue(request.body.contains("name=\"audio\""))
            assertTrue(request.body.contains("filename=\"recording.wav\""))
            assertTrue(request.body.contains("Content-Type: audio/wav"))
            assertTrue(request.body.contains("name=\"lang\""))
            assertTrue(request.body.contains("\r\n\r\nen\r\n"))
            assertEquals("entry-1", capture.entryId)
            assertEquals(Transcript("marché 42 euros et pain 1,20", "en"), capture.transcript)
            assertEquals(
                listOf(
                    ExpenseDraft("exp-1", 4_200, "Marché", LocalDate(2026, 11, 15), "cat-1", "marche"),
                    ExpenseDraft("exp-2", 120, "Pain", LocalDate(2026, 11, 14), null, "pain"),
                ),
                capture.drafts,
            )
        }

    @Test
    fun voiceCaptureDescriptorDeclaresRemoteCost() {
        val engine = NemlaVoiceCaptureEngine(RemoteHarness { json("{}") }.api)
        assertEquals(AiCapability.VoiceExpenseCapture, engine.capability)
        assertEquals(ExecutionLocation.REMOTE, engine.descriptor.location)
        assertEquals(NEMLA_PROVIDER, engine.descriptor.provider)
        assertEquals(1, engine.descriptor.creditCost)
    }

    @Test
    fun nonEnglishLanguagesAreSentAsFrench() =
        runTest {
            val harness = RemoteHarness { json(voiceResponse) }
            NemlaVoiceCaptureEngine(harness.api).execute(speech("de-DE"))
            assertTrue(harness.single.body.contains("\r\n\r\nfr\r\n"))
        }

    @Test
    fun voiceQuotaIsReportedAsQuotaExceeded() =
        runTest {
            val harness =
                RemoteHarness { json("""{"error":"Quota de dictées atteint pour ce mois (30)."}""", HttpStatusCode.TooManyRequests) }
            assertEquals(AiError.QuotaExceeded, expectAiError { NemlaVoiceCaptureEngine(harness.api).execute(speech()) })
        }

    @Test
    fun voiceKnownRejectionsCarryOnlyTheCode() =
        runTest {
            val harness =
                RemoteHarness {
                    json(
                        """{"error":"Aucune parole détectée dans l'enregistrement","voice_entry_id":"v"}""",
                        HttpStatusCode.UnprocessableEntity,
                    )
                }
            assertEquals(AiError.Rejected("NO_SPEECH_DETECTED"), expectAiError { NemlaVoiceCaptureEngine(harness.api).execute(speech()) })
        }

    @Test
    fun voiceUnknownServerMessageNeverLeaks() =
        runTest {
            val harness = RemoteHarness { json("""{"error":"jean@example.com: insert failed"}""", HttpStatusCode.BadRequest) }
            val error = expectAiError { NemlaVoiceCaptureEngine(harness.api).execute(speech()) }
            assertEquals(AiError.Rejected("INVALID_INPUT"), error)
            assertFalse(error.toString().contains("jean"))
        }

    @Test
    fun aiGateway401IsAnEngineFailureNotASignOut() =
        runTest {
            val harness =
                RemoteHarness { json("""{"error":"Transcription impossible (401). invalid key"}""", HttpStatusCode.Unauthorized) }
            val error = expectAiError { NemlaVoiceCaptureEngine(harness.api).execute(speech()) }
            assertEquals(AiError.EngineFailure("server_401"), error)
            assertEquals(0, harness.tokens.refreshCount)
            assertFalse(harness.tokens.invalidated)
        }

    @Test
    fun rejectedSessionIsUnauthorized() =
        runTest {
            val harness = RemoteHarness { json("""{"error":"Session invalide"}""", HttpStatusCode.Unauthorized) }
            assertEquals(AiError.Unauthorized, expectAiError { NemlaVoiceCaptureEngine(harness.api).execute(speech()) })
            assertEquals(2, harness.requests.size)
        }

    @Test
    fun transportFailureIsOffline() =
        runTest {
            val harness = RemoteHarness { throw IOException("unreachable") }
            assertEquals(AiError.Offline, expectAiError { NemlaVoiceCaptureEngine(harness.api).execute(speech()) })
        }

    @Test
    fun htmlErrorPageIsAnEngineFailure() =
        runTest {
            val harness = RemoteHarness { html(HttpStatusCode.InternalServerError) }
            val error = expectAiError { NemlaVoiceCaptureEngine(harness.api).execute(speech()) }
            assertEquals(AiError.EngineFailure("server_500"), error)
            assertFalse(error.toString().contains("jean"))
        }

    @Test
    fun aiRateLimitIsAnEngineFailure() =
        runTest {
            val harness = RemoteHarness { json("""{"error":"Analyse IA impossible (429)."}""", HttpStatusCode.TooManyRequests) }
            assertEquals(
                AiError.EngineFailure(REASON_RATE_LIMITED),
                expectAiError { NemlaVoiceCaptureEngine(harness.api).execute(speech()) },
            )
        }

    @Test
    fun malformedVoiceResponseIsAnEngineFailure() =
        runTest {
            val harness =
                RemoteHarness { json("""{"transcript":"x","expenses":[{"montant":"abc","description":"x","date":"2026-11-15"}]}""") }
            assertEquals(
                AiError.EngineFailure(REASON_INVALID_RESPONSE),
                expectAiError { NemlaVoiceCaptureEngine(harness.api).execute(speech()) },
            )
            val badDate = RemoteHarness { json("""{"transcript":"x","expenses":[{"montant":3,"description":"x","date":"15/11"}]}""") }
            assertEquals(
                AiError.EngineFailure(REASON_INVALID_RESPONSE),
                expectAiError { NemlaVoiceCaptureEngine(badDate.api).execute(speech()) },
            )
        }

    @Test
    fun cancellationIsPropagated() =
        runTest {
            val harness = RemoteHarness { throw CancellationException("stop") }
            assertFailsWith<CancellationException> { NemlaVoiceCaptureEngine(harness.api).execute(speech()) }
        }

    @Test
    fun transcriptionReadsTheTextObject() =
        runTest {
            val harness = RemoteHarness { json("""{"transcript":{"text":"Netflix 13,49 par mois","inputTokens":3,"outputTokens":2}}""") }
            val engine = NemlaTranscriptionEngine(harness.api)
            assertEquals(Transcript("Netflix 13,49 par mois", "fr"), engine.execute(speech()))
            assertEquals(
                "https://api.example.com/api/public/transcribe",
                harness.single.data.url
                    .toString(),
            )
            assertTrue(harness.single.body.contains("filename=\"recording.wav\""))
            assertEquals(AiCapability.SpeechToText, engine.capability)
            assertEquals(0, engine.descriptor.creditCost)
        }

    @Test
    fun transcriptionAcceptsAPlainString() =
        runTest {
            val harness = RemoteHarness { json("""{"transcript":"Loyer 650"}""") }
            assertEquals("Loyer 650", NemlaTranscriptionEngine(harness.api).execute(speech()).text)
        }

    @Test
    fun transcriptionWithoutTextIsAnEngineFailure() =
        runTest {
            val harness = RemoteHarness { json("""{"transcript":{"inputTokens":3}}""") }
            assertEquals(
                AiError.EngineFailure(REASON_INVALID_RESPONSE),
                expectAiError { NemlaTranscriptionEngine(harness.api).execute(speech()) },
            )
            val unauthorized = RemoteHarness { json("""{"error":"Authorization Bearer token manquant"}""", HttpStatusCode.Unauthorized) }
            assertEquals(AiError.Unauthorized, expectAiError { NemlaTranscriptionEngine(unauthorized.api).execute(speech()) })
        }

    @Test
    fun recurringParsingPostsJsonAndMapsCharges() =
        runTest {
            val harness =
                RemoteHarness {
                    json(
                        """{"charges":[
                          {"nom":"Netflix","montant":13.49,"frequence":"mensuelle","jour_prelevement":5,"category_nom":"Abonnements","category_id":"cat-9"},
                          {"nom":"Assurance auto","montant":480,"frequence":"annuelle","jour_prelevement":31,"category_nom":"Assurances","category_id":null}]}""",
                    )
                }
            val engine = NemlaRecurringParsingEngine(harness.api)
            val parsed = engine.execute(RecurringParsingRequest("  Netflix 13,49 et assurance 480 par an ", "en"))
            val request = harness.single
            assertEquals(HttpMethod.Post, request.data.method)
            assertEquals("https://api.example.com/api/public/parse/recurring", request.data.url.toString())
            assertEquals("""{"text":"Netflix 13,49 et assurance 480 par an","lang":"en"}""", request.body)
            assertEquals(
                listOf(
                    RecurringChargeDraft("Netflix", 1_349, ChargeFrequency.MONTHLY, 5, "cat-9"),
                    RecurringChargeDraft("Assurance auto", 48_000, ChargeFrequency.YEARLY, 28, null),
                ),
                parsed.charges,
            )
            assertEquals(0, engine.descriptor.creditCost)
        }

    @Test
    fun recurringTextOutsideServerLimitsIsRejectedLocally() =
        runTest {
            val harness = RemoteHarness { json("""{"charges":[]}""") }
            val engine = NemlaRecurringParsingEngine(harness.api)
            assertEquals(AiError.Rejected("INVALID_INPUT"), expectAiError { engine.execute(RecurringParsingRequest(" a ", "fr")) })
            assertEquals(
                AiError.Rejected("INVALID_INPUT"),
                expectAiError { engine.execute(RecurringParsingRequest("x".repeat(4_001), "fr")) },
            )
            assertTrue(harness.requests.isEmpty())
        }

    @Test
    fun recurringWithoutCategoriesIsRejected() =
        runTest {
            val harness = RemoteHarness { json("""{"error":"Aucune catégorie"}""", HttpStatusCode.BadRequest) }
            assertEquals(
                AiError.Rejected("NO_CATEGORY"),
                expectAiError { NemlaRecurringParsingEngine(harness.api).execute(RecurringParsingRequest("Loyer 650", "fr")) },
            )
        }

    @Test
    fun budgetSuggestionMapsAmountsToCents() =
        runTest {
            val harness =
                RemoteHarness {
                    json(
                        """{"monthly":1230,"annual":14760,"fixed_monthly":845.5,
                           "months":[{"month":"2026-08","total":1234.56},{"month":"2026-09","total":1100.1}],
                           "per_category":[{"category_id":"cat-1","monthly":120,"months":3}],
                           "basis":"moyenne_2_mois"}""",
                    )
                }
            val engine = NemlaBudgetSuggestionEngine(harness.api)
            val estimate = engine.execute(BudgetSuggestionRequest)
            assertEquals(HttpMethod.Get, harness.single.data.method)
            assertEquals(
                "https://api.example.com/api/public/budget/suggest",
                harness.single.data.url
                    .toString(),
            )
            assertEquals(
                BudgetEstimate(
                    monthlyCents = 123_000,
                    annualCents = 1_476_000,
                    fixedMonthlyCents = 84_550,
                    months = listOf(MonthTotal("2026-08", 123_456), MonthTotal("2026-09", 110_010)),
                    perCategory = listOf(CategoryEstimate("cat-1", 12_000, 3)),
                    basis = "moyenne_2_mois",
                ),
                estimate,
            )
            assertEquals(AiCapability.BudgetSuggestion, engine.capability)
        }

    @Test
    fun budgetSuggestionServerErrorIsAnEngineFailure() =
        runTest {
            val harness = RemoteHarness { json("""{"error":"relation does not exist"}""", HttpStatusCode.InternalServerError) }
            assertEquals(
                AiError.EngineFailure("server_500"),
                expectAiError {
                    NemlaBudgetSuggestionEngine(harness.api).execute(BudgetSuggestionRequest)
                },
            )
        }

    private val reportResponse =
        """
        {"month":"2026-10","kind":"mois","previous_month":"2026-09","total":1234.5,"previous_total":1000,"budget":1500,
         "expense_count":42,
         "categories":[{"nom":"Courses","montant":400.25,"part":32,"variation":-12.5}],
         "highlights":["Courses concentre 32 % de vos dépenses (400 €)."],"advice":["Rien à corriger ce mois-ci : continuez à dicter régulièrement."]}
        """.trimIndent()

    @Test
    fun monthlyReportUsesTheMonthParameter() =
        runTest {
            val harness = RemoteHarness { json(reportResponse) }
            val engine = NemlaPeriodReportEngine(harness.api)
            val review = engine.execute(PeriodReportRequest("2026-10", ReviewPeriodKind.MONTH))
            assertEquals(
                "https://api.example.com/api/public/report/monthly?month=2026-10",
                harness.single.data.url
                    .toString(),
            )
            assertEquals("2026-10", review.period)
            assertEquals(ReviewPeriodKind.MONTH, review.kind)
            assertEquals("2026-09", review.previousPeriod)
            assertEquals(123_450, review.totalCents)
            assertEquals(100_000, review.previousTotalCents)
            assertEquals(150_000, review.budgetCents)
            assertEquals(42, review.expenseCount)
            assertEquals(listOf(CategoryReview("Courses", 40_025, 32.0, -12.5)), review.categories)
            assertEquals(1, review.highlights.size)
            assertEquals(AiCapability.PeriodReport, engine.capability)
        }

    @Test
    fun yearlyReportUsesTheYearParameter() =
        runTest {
            val harness =
                RemoteHarness {
                    json(
                        """{"month":"2025","kind":"annee","previous_month":"2024","total":0,"previous_total":0,"budget":0,"expense_count":0}""",
                    )
                }
            val review = NemlaPeriodReportEngine(harness.api).execute(PeriodReportRequest("2025", ReviewPeriodKind.YEAR))
            assertEquals(
                "https://api.example.com/api/public/report/monthly?year=2025",
                harness.single.data.url
                    .toString(),
            )
            assertEquals(ReviewPeriodKind.YEAR, review.kind)
            assertTrue(review.categories.isEmpty())
        }

    @Test
    fun malformedPeriodIsRejectedLocally() =
        runTest {
            val harness = RemoteHarness { json(reportResponse) }
            val engine = NemlaPeriodReportEngine(harness.api)
            assertEquals(
                AiError.Rejected("INVALID_INPUT"),
                expectAiError { engine.execute(PeriodReportRequest("2026-13", ReviewPeriodKind.MONTH)) },
            )
            assertEquals(
                AiError.Rejected("INVALID_INPUT"),
                expectAiError { engine.execute(PeriodReportRequest("2026-10", ReviewPeriodKind.YEAR)) },
            )
            assertTrue(harness.requests.isEmpty())
        }

    @Test
    fun reportOfflineIsOffline() =
        runTest {
            val harness = RemoteHarness { throw IOException("down") }
            assertEquals(
                AiError.Offline,
                expectAiError { NemlaPeriodReportEngine(harness.api).execute(PeriodReportRequest("2026-10", ReviewPeriodKind.MONTH)) },
            )
        }

    @Test
    fun koinModuleRegistersAllFiveEnginesAsAiEngines() {
        val harness = RemoteHarness { json("{}") }
        val koin =
            koinApplication {
                modules(
                    module {
                        single { harness.client }
                        single { remoteConfig }
                    },
                    aiRemoteNemlaModule,
                )
            }.koin
        val engines = koin.getAll<AiEngine<*, *>>()
        assertEquals(
            setOf(
                AiCapability.VoiceExpenseCapture,
                AiCapability.SpeechToText,
                AiCapability.RecurringParsing,
                AiCapability.BudgetSuggestion,
                AiCapability.PeriodReport,
            ),
            engines.map { it.capability }.toSet(),
        )
        assertEquals(5, engines.map { it.descriptor.id }.toSet().size)
        assertTrue(engines.none { it.capability == AiCapability.StatementParsing })
        assertTrue(engines.all { it.descriptor.location == ExecutionLocation.REMOTE })
    }
}
