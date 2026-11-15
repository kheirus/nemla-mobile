package app.nemla.core.network

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.time.Clock
import kotlin.time.Instant

internal const val SUPABASE = "https://proj.supabase.co"
internal const val API = "https://api.nemla.app"
internal const val PUBLISHABLE_KEY = "sb_publishable_test"

internal val testConfig = BackendConfig(SUPABASE, PUBLISHABLE_KEY, API)

internal val farFuture: Instant = Instant.fromEpochSeconds(4_000_000_000)

internal class FakeSessionTokenStore(
    var current: AccessToken? = AccessToken("token-1", farFuture),
    private val next: () -> AppResult<AccessToken> = { AppResult.Success(AccessToken("token-2", farFuture)) },
) : SessionTokenStore {
    var refreshCount = 0
    var invalidated = false

    override suspend fun currentAccessToken(): AccessToken? = current

    override suspend fun refreshAccessToken(): AppResult<AccessToken> {
        refreshCount++
        val result = next()
        if (result is AppResult.Success) current = result.value
        return result
    }

    override suspend fun invalidate() {
        invalidated = true
        current = null
    }
}

internal class FixedClock(
    var now: Instant,
) : Clock {
    override fun now(): Instant = now
}

internal class Harness(
    val store: FakeSessionTokenStore = FakeSessionTokenStore(),
    clock: Clock = FixedClock(Instant.fromEpochSeconds(1_700_000_000)),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val requests = mutableListOf<HttpRequestData>()
    val engine =
        MockEngine { request ->
            requests += request
            handler(request)
        }
    val tokens: TokenProvider = createTokenProvider(lazyOf(store), clock)
    val client: HttpClient = createHttpClient(testConfig, tokens, engine = engine)
}

internal fun MockRequestHandleScope.json(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

internal fun MockRequestHandleScope.html(status: HttpStatusCode): HttpResponseData =
    respond("<html><body>Erreur</body></html>", status, headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()))

internal val AppResult<*>.failure: AppError?
    get() = (this as? AppResult.Failure)?.error
