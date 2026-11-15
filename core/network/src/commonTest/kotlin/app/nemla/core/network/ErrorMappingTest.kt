package app.nemla.core.network

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErrorMappingTest {
    @Serializable
    private data class Quota(
        val month: String,
        @SerialName("voice_limit") val voiceLimit: Int,
    )

    private suspend fun mapped(
        url: String,
        status: HttpStatusCode,
        body: String,
    ): AppError? {
        val harness = Harness { json(body, status) }
        return safeRequest<Unit> { harness.client.post(url) }.failure
    }

    private val auth = "$SUPABASE/auth/v1/token?grant_type=password"
    private val rest = "$SUPABASE/rest/v1/categories"
    private val voice = "$API/api/public/voice/process"

    @Test
    fun decodesSuccessfulBodiesIgnoringUnknownKeys() =
        runTest {
            val harness = Harness { json("""{"month":"2026-11","voice_limit":30,"extra":true}""") }
            val result = safeRequest<Quota> { harness.client.get("$SUPABASE/rest/v1/rpc/ensure_month_quota") }
            assertEquals(AppResult.Success(Quota("2026-11", 30)), result)
        }

    @Test
    fun malformedSuccessBodyIsAServerError() =
        runTest {
            val harness = Harness { html(HttpStatusCode.OK) }
            assertEquals(AppError.Server(200), safeRequest<Quota> { harness.client.get(rest) }.failure)
        }

    @Test
    fun mapsGoTrueErrors() =
        runTest {
            val bad = HttpStatusCode.BadRequest
            assertEquals(
                AppError.Validation(ValidationCode.INVALID_CREDENTIALS),
                mapped(auth, bad, """{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}"""),
            )
            assertEquals(
                AppError.Validation(ValidationCode.INVALID_CREDENTIALS),
                mapped(auth, bad, """{"error":"invalid_grant","error_description":"Invalid login credentials"}"""),
            )
            assertEquals(
                AppError.Validation(ValidationCode.EMAIL_NOT_CONFIRMED),
                mapped(auth, bad, """{"error_code":"email_not_confirmed","msg":"Email not confirmed"}"""),
            )
            assertEquals(
                AppError.Validation(ValidationCode.EMAIL_ALREADY_REGISTERED),
                mapped(auth, HttpStatusCode.UnprocessableEntity, """{"error_code":"user_already_exists"}"""),
            )
            assertEquals(
                AppError.Validation(ValidationCode.PASSWORD_TOO_WEAK),
                mapped(auth, HttpStatusCode.UnprocessableEntity, """{"error_code":"weak_password"}"""),
            )
            assertEquals(
                AppError.RateLimited,
                mapped(auth, HttpStatusCode.TooManyRequests, """{"error_code":"over_email_send_rate_limit"}"""),
            )
            assertEquals(AppError.Unauthorized, mapped(auth, bad, """{"error_code":"refresh_token_not_found"}"""))
            assertEquals(
                AppError.Unauthorized,
                mapped(auth, bad, """{"error":"invalid_grant","error_description":"Invalid Refresh Token"}"""),
            )
            assertEquals(AppError.Forbidden, mapped(auth, HttpStatusCode.UnprocessableEntity, """{"error_code":"signup_disabled"}"""))
        }

    @Test
    fun mapsPostgrestErrors() =
        runTest {
            assertEquals(AppError.NotFound, mapped(rest, HttpStatusCode.NotAcceptable, """{"code":"PGRST116","message":"0 rows"}"""))
            assertEquals(
                AppError.Validation(ValidationCode.ALREADY_EXISTS),
                mapped(
                    rest,
                    HttpStatusCode.Conflict,
                    """{"code":"23505","message":"duplicate key","details":"Key (user_id, nom)=(x, Loyer)"}""",
                ),
            )
            assertEquals(AppError.Forbidden, mapped(rest, HttpStatusCode.Forbidden, """{"code":"42501","message":"rls"}"""))
            assertEquals(
                AppError.Validation(ValidationCode.INVALID_INPUT),
                mapped(rest, HttpStatusCode.BadRequest, """{"code":"22P02","message":"invalid input syntax"}"""),
            )
            assertEquals(
                AppError.Unauthorized,
                mapped(rest, HttpStatusCode.BadRequest, """{"code":"P0001","message":"Non authentifié"}"""),
            )
            assertEquals(AppError.Server(503), mapped(rest, HttpStatusCode.ServiceUnavailable, """{"code":"PGRST002"}"""))
        }

    @Test
    fun mapsNemlaEndpointErrors() =
        runTest {
            assertEquals(
                AppError.QuotaExceeded,
                mapped(voice, HttpStatusCode.TooManyRequests, """{"error":"Quota de dictées atteint pour ce mois (30)."}"""),
            )
            assertEquals(
                AppError.RateLimited,
                mapped(voice, HttpStatusCode.TooManyRequests, """{"error":"Analyse IA impossible (429)."}"""),
            )
            assertEquals(
                AppError.Server(402),
                mapped(voice, HttpStatusCode.PaymentRequired, """{"error":"Analyse IA impossible (402)."}"""),
            )
            assertEquals(
                AppError.Validation(ValidationCode.PAYLOAD_TOO_LARGE),
                mapped(voice, HttpStatusCode.PayloadTooLarge, """{"error":"Enregistrement trop volumineux (60 s max)"}"""),
            )
            assertEquals(
                AppError.Validation(ValidationCode.NO_SPEECH_DETECTED),
                mapped(
                    voice,
                    HttpStatusCode.UnprocessableEntity,
                    """{"error":"Aucune parole détectée dans l'enregistrement","voice_entry_id":"v"}""",
                ),
            )
            assertEquals(
                AppError.Validation(ValidationCode.NO_EXPENSE_DETECTED),
                mapped(voice, HttpStatusCode.UnprocessableEntity, """{"error":"Aucune dépense détectée"}"""),
            )
            assertEquals(
                AppError.Validation(ValidationCode.PERIOD_TOO_LONG),
                mapped(
                    "$API/api/public/import/file",
                    HttpStatusCode.UnprocessableEntity,
                    """{"error":"Ce fichier couvre 3 mois (2026-01 → 2026-03)."}""",
                ),
            )
            assertEquals(
                AppError.Validation(ValidationCode.UNREADABLE_FILE),
                mapped(
                    "$API/api/public/import/file",
                    HttpStatusCode.UnprocessableEntity,
                    """{"error":"Impossible de lire ce fichier (PDF scanné ou vide ?)."}""",
                ),
            )
            assertEquals(
                AppError.Validation(ValidationCode.NO_CATEGORY),
                mapped("$API/api/public/parse/recurring", HttpStatusCode.BadRequest, """{"error":"Aucune catégorie"}"""),
            )
            assertEquals(
                AppError.Validation(ValidationCode.INVALID_INPUT),
                mapped("$API/api/public/parse/recurring", HttpStatusCode.BadRequest, """{"error":"Texte invalide"}"""),
            )
            assertEquals(AppError.Server(500), mapped(voice, HttpStatusCode.InternalServerError, """{"error":"Backend non configuré"}"""))
        }

    @Test
    fun htmlErrorPagesFallBackToStatus() =
        runTest {
            val harness = Harness { html(HttpStatusCode.InternalServerError) }
            assertEquals(AppError.Server(500), safeRequest<Unit> { harness.client.get("$API/api/public/report/monthly") }.failure)
        }

    @Test
    fun mappedErrorsNeverCarryServerMessages() =
        runTest {
            val error = mapped(voice, HttpStatusCode.BadRequest, """{"error":"jane@example.fr a un problème"}""")
            assertFalse(error.toString().contains("jane"))
        }

    @Test
    fun mapsTransportFailures() =
        runTest {
            val offline = Harness { throw IOException("unreachable") }
            assertEquals(AppError.Network.Offline, safeRequest<Unit> { offline.client.get(rest) }.failure)
            val timeout = Harness { throw HttpRequestTimeoutException(rest, 1_000) }
            assertEquals(AppError.Network.Timeout, safeRequest<Unit> { timeout.client.get(rest) }.failure)
            val other = Harness { throw IllegalStateException("boom") }
            assertEquals(AppError.Unknown, safeRequest<Unit> { other.client.get(rest) }.failure)
        }

    @Test
    fun cancellationIsAlwaysRethrown() =
        runTest {
            val harness = Harness { throw CancellationException("cancelled") }
            assertFailsWith<CancellationException> { safeRequest<Unit> { harness.client.get(rest) } }
        }

    @Test
    fun cleartextRequestsAreRefusedBeforeLeavingTheDevice() =
        runTest {
            val harness = Harness { json("{}") }
            val result = safeRequest<Unit> { harness.client.get("http://proj.supabase.co/rest/v1/categories") }
            assertEquals(AppError.Configuration, result.failure)
            assertTrue(harness.requests.isEmpty())
        }

    @Test
    fun backendConfigRequiresHttps() {
        assertTrue(testConfig.isComplete)
        assertFalse(BackendConfig("http://proj.supabase.co", PUBLISHABLE_KEY, API).isComplete)
        assertFalse(BackendConfig(SUPABASE, PUBLISHABLE_KEY, "http://api.nemla.app").isComplete)
        assertFalse(BackendConfig(SUPABASE, "", API).isComplete)
        assertFalse(BackendConfig("", "", "").isComplete)
        assertFalse(testConfig.toString().contains(PUBLISHABLE_KEY))
    }
}
