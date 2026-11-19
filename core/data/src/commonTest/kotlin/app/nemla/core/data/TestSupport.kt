package app.nemla.core.data

import app.nemla.core.common.AppError
import app.nemla.core.common.AppLogger
import app.nemla.core.common.AppResult
import app.nemla.core.common.LogAttribute
import app.nemla.core.common.LogEvent
import app.nemla.core.common.LogLevel
import app.nemla.core.common.NoOpLogger
import app.nemla.core.data.consent.SecureStoreAiConsentRepository
import app.nemla.core.data.remote.KtorCategoryRepository
import app.nemla.core.data.remote.KtorDeferredDebitRepository
import app.nemla.core.data.remote.KtorExpenseRepository
import app.nemla.core.data.remote.KtorExpenseReviewRepository
import app.nemla.core.data.remote.KtorInboxRepository
import app.nemla.core.data.remote.KtorProfileRepository
import app.nemla.core.data.remote.KtorQuotaRepository
import app.nemla.core.data.remote.KtorRecurringChargeRepository
import app.nemla.core.data.remote.KtorReportRepository
import app.nemla.core.data.remote.Postgrest
import app.nemla.core.data.session.GoTrueApi
import app.nemla.core.data.session.KtorAuthRepository
import app.nemla.core.data.session.SessionManager
import app.nemla.core.data.session.SessionStorage
import app.nemla.core.network.BackendConfig
import app.nemla.core.network.TokenProvider
import app.nemla.core.network.createHttpClient
import app.nemla.core.network.createTokenProvider
import app.nemla.core.security.InMemorySecureStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.time.Clock
import kotlin.time.Instant

internal const val SUPABASE = "https://project.example.com"
internal const val API = "https://api.example.com"
internal const val PUBLISHABLE_KEY = "sb_publishable_test"
internal const val USER_ID = "user-1"

internal val testConfig = BackendConfig(SUPABASE, PUBLISHABLE_KEY, API)

internal val now: Instant = Instant.fromEpochSeconds(1_800_000_000)

internal class FixedClock(
    var instant: Instant = now,
) : Clock {
    override fun now(): Instant = instant
}

internal fun storedSessionJson(
    access: String = "access-1",
    refresh: String = "refresh-1",
    expiresAt: Long = now.epochSeconds + 3_600,
    userId: String = USER_ID,
): String =
    """{"access_token":"$access","refresh_token":"$refresh","expires_at":$expiresAt,"user_id":"$userId","email":"jane@example.com"}"""

internal fun signedInStore(json: String = storedSessionJson()): InMemorySecureStore = InMemorySecureStore(mapOf(SessionStorage.KEY to json))

internal fun sessionResponse(
    access: String = "access-2",
    refresh: String = "refresh-2",
    userId: String = USER_ID,
): String =
    """{"access_token":"$access","token_type":"bearer","expires_in":3600,"refresh_token":"$refresh",
       "user":{"id":"$userId","email":"jane@example.com"}}"""

internal class RecordedRequest(
    val data: HttpRequestData,
    val body: String,
) {
    val path: String get() = data.url.encodedPath
    val method: String get() = data.method.value
    val authorization: String? get() = data.headers[HttpHeaders.Authorization]

    fun query(name: String): String? = data.url.parameters[name]
}

internal class DataHarness(
    val store: InMemorySecureStore = InMemorySecureStore(),
    val clock: FixedClock = FixedClock(),
    private val handler: suspend MockRequestHandleScope.(RecordedRequest) -> HttpResponseData,
) {
    val requests = mutableListOf<RecordedRequest>()
    private val engine =
        MockEngine { request ->
            val recorded = RecordedRequest(request, request.body.toByteArray().decodeToString())
            requests += recorded
            handler(recorded)
        }
    private lateinit var sessionManager: SessionManager
    val tokens: TokenProvider = createTokenProvider(lazy { sessionManager }, clock)
    val client: HttpClient = createHttpClient(testConfig, tokens, engine = engine)
    val goTrue = GoTrueApi(client, testConfig)
    val storage = SessionStorage(store, NoOpLogger)
    val consents = SecureStoreAiConsentRepository(store, clock, NoOpLogger)
    val sessions: SessionManager =
        SessionManager(goTrue, storage, clock, NoOpLogger) { consents.clear() }.also { sessionManager = it }
    val auth = KtorAuthRepository(goTrue, sessions, tokens, clock, NoOpLogger)
    val postgrest = Postgrest(client, testConfig, sessions)
    val profiles = KtorProfileRepository(postgrest, goTrue, NoOpLogger)
    val categories = KtorCategoryRepository(postgrest)
    val quotas = KtorQuotaRepository(postgrest)
    val reviews = KtorExpenseReviewRepository(postgrest)
    val expenses = KtorExpenseRepository(postgrest)
    val recurring = KtorRecurringChargeRepository(postgrest)
    val debits = KtorDeferredDebitRepository(postgrest)
    val reports = KtorReportRepository(postgrest)
    val inbox = KtorInboxRepository(postgrest, profiles, clock)

    fun on(
        method: String,
        path: String,
    ): List<RecordedRequest> = requests.filter { it.method == method && it.path == path }
}

internal fun MockRequestHandleScope.json(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

internal fun MockRequestHandleScope.noContent(): HttpResponseData = respond("", HttpStatusCode.NoContent)

internal val AppResult<*>.failure: AppError?
    get() = (this as? AppResult.Failure)?.error

internal class RecordingLogger : AppLogger {
    val lines = mutableListOf<String>()

    override fun <E> log(
        level: LogLevel,
        event: E,
        attributes: List<LogAttribute>,
    ) where E : Enum<E>, E : LogEvent {
        lines += "${level.name} ${event.name} ${attributes.joinToString(" ")}"
    }
}
