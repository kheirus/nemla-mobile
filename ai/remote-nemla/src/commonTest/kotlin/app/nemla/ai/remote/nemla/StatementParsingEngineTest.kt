package app.nemla.ai.remote.nemla

import app.nemla.ai.api.AiCapability
import app.nemla.ai.api.AiError
import app.nemla.ai.api.AnonymizedText
import app.nemla.ai.api.CategoryHint
import app.nemla.ai.api.EngineAvailability
import app.nemla.ai.api.ExecutionLocation
import app.nemla.ai.api.ExpenseDraft
import app.nemla.ai.api.RawStatement
import app.nemla.ai.api.StatementParsingRequest
import app.nemla.ai.local.RuleBasedStatementAnonymizer
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class StatementParsingEngineTest {
    private val rawText =
        "BANQUE EXEMPLE\nM. CAMILLE TESTEUR\nRelevé de compte du 1 mars 2026 au 31 mars 2026\nDate Libellé Débit Crédit\n" +
            "02/03/2026 CB BOULANGERIE TEST 3,80\n05/03/2026 VIR SEPA FR7630006000011234567890189 120,00\n" +
            "06/03/2026 PRLV OPERATEUR jean.testeur@example.com 19,99"

    private val response =
        """
        {"kind":"texte","redactions":4,
         "expenses":[
           {"id":"exp-1","montant":3.8,"description":"Boulangerie test","date":"2026-03-02","category_id":"cat-1","source":"manual"},
           {"id":"exp-2","montant":"19.99","description":"Opérateur","date":"2026-03-06","category_id":null,"source":"manual"}]}
        """.trimIndent()

    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.parse("2026-03-31T22:30:00Z")
        }

    private suspend fun anonymized(text: String = rawText): AnonymizedText = RuleBasedStatementAnonymizer().execute(RawStatement(text))

    private suspend fun request(
        language: String = "fr-FR",
        text: String = rawText,
    ): StatementParsingRequest = StatementParsingRequest(anonymized(text), language, listOf(CategoryHint("cat-1", "Courses")))

    private fun engine(harness: RemoteHarness): NemlaStatementParsingEngine =
        NemlaStatementParsingEngine(harness.api, clock) { TimeZone.of("Europe/Paris") }

    @Test
    fun sendsOnlyTheAnonymizedTextAsJsonAndMapsTheInsertedExpenses() =
        runTest {
            val harness = RemoteHarness { json(response) }
            val input = request(language = "en-GB")
            val parsed = engine(harness).execute(input)
            val sent = harness.single
            assertEquals(HttpMethod.Post, sent.data.method)
            assertEquals("https://api.example.com/api/public/import/text", sent.data.url.toString())
            assertEquals("Bearer token-1", sent.data.headers[HttpHeaders.Authorization])
            assertTrue(
                sent.data.body.contentType
                    ?.match(ContentType.Application.Json) == true,
            )
            val body = Json.parseToJsonElement(sent.body).jsonObject
            assertEquals(setOf("text", "lang", "today"), body.keys)
            assertEquals(input.statement.value, body.getValue("text").jsonPrimitive.content)
            assertEquals("en", body.getValue("lang").jsonPrimitive.content)
            assertEquals("2026-04-01", body.getValue("today").jsonPrimitive.content)
            assertFalse(sent.body.contains("FR7630006000011234567890189"))
            assertFalse(sent.body.contains("TESTEUR"))
            assertFalse(sent.body.contains("example.com"))
            assertTrue(sent.body.contains("BOULANGERIE TEST 3,80"))
            assertEquals(
                listOf(
                    ExpenseDraft("exp-1", 380, "Boulangerie test", LocalDate(2026, 3, 2), "cat-1"),
                    ExpenseDraft("exp-2", 1_999, "Opérateur", LocalDate(2026, 3, 6), null),
                ),
                parsed.drafts,
            )
        }

    @Test
    fun descriptorIsRemoteAndFreeForTheRouterCredits() {
        val engine = engine(RemoteHarness { json(response) })
        assertEquals(AiCapability.StatementParsing, engine.capability)
        assertEquals(ExecutionLocation.REMOTE, engine.descriptor.location)
        assertEquals(0, engine.descriptor.creditCost)
    }

    @Test
    fun availabilityProbesTheEndpointWithAnEmptyBody() =
        runTest {
            val missing = RemoteHarness { json("""{"error":"Not found"}""", HttpStatusCode.NotFound) }
            assertEquals(EngineAvailability.Unavailable(REASON_NOT_SUPPORTED), engine(missing).availability())
            assertEquals(JsonObject(emptyMap()), Json.parseToJsonElement(missing.single.body))

            val notAllowed = RemoteHarness { json("{}", HttpStatusCode.MethodNotAllowed) }
            assertEquals(EngineAvailability.Unavailable(REASON_NOT_SUPPORTED), engine(notAllowed).availability())

            val present = RemoteHarness { json("""{"error":"Texte manquant"}""", HttpStatusCode.BadRequest) }
            assertEquals(EngineAvailability.Ready, engine(present).availability())

            val offline = RemoteHarness { throw IOException("down") }
            assertEquals(EngineAvailability.Ready, engine(offline).availability())
        }

    @Test
    fun missingEndpointDuringTheCallIsNotSupported() =
        runTest {
            val harness = RemoteHarness { html(HttpStatusCode.NotFound) }
            assertEquals(AiError.EngineFailure(REASON_NOT_SUPPORTED), expectAiError { engine(harness).execute(request()) })
        }

    @Test
    fun serverRejectionsKeepTheirMeaning() =
        runTest {
            suspend fun errorFor(
                status: HttpStatusCode,
                message: String,
            ): AiError {
                val harness = RemoteHarness { json("""{"error":"$message"}""", status) }
                return expectAiError { engine(harness).execute(request()) }
            }
            assertEquals(
                AiError.QuotaExceeded,
                errorFor(HttpStatusCode.TooManyRequests, "Quota d'import de relevé atteint pour ce mois (1). Il repart à zéro le 1er."),
            )
            assertEquals(AiError.EngineFailure(REASON_RATE_LIMITED), errorFor(HttpStatusCode.TooManyRequests, "Rate limited"))
            assertEquals(AiError.Rejected("NO_EXPENSE_DETECTED"), errorFor(HttpStatusCode.UnprocessableEntity, "Aucune dépense détectée"))
            assertEquals(
                AiError.Rejected("PERIOD_TOO_LONG"),
                errorFor(HttpStatusCode.UnprocessableEntity, "Ce fichier couvre 3 mois (2026-01 → 2026-03)."),
            )
            assertEquals(AiError.Rejected("PAYLOAD_TOO_LARGE"), errorFor(HttpStatusCode.PayloadTooLarge, "Texte trop volumineux"))
            assertEquals(AiError.Rejected("NO_CATEGORY"), errorFor(HttpStatusCode.BadRequest, "Aucune catégorie configurée"))
        }

    @Test
    fun tooShortTextsNeverLeaveTheDevice() =
        runTest {
            val harness = RemoteHarness { json(response) }
            assertEquals(
                AiError.Rejected("UNREADABLE_FILE"),
                expectAiError { engine(harness).execute(request(text = " \n ")) },
            )
            assertTrue(harness.requests.isEmpty())
        }
}
