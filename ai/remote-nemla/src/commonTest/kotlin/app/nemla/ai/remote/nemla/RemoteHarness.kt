package app.nemla.ai.remote.nemla

import app.nemla.ai.api.AiError
import app.nemla.ai.api.AiException
import app.nemla.ai.api.AudioClip
import app.nemla.ai.api.AudioEncoding
import app.nemla.ai.api.SpeechRequest
import app.nemla.core.common.AppResult
import app.nemla.core.network.AccessToken
import app.nemla.core.network.BackendConfig
import app.nemla.core.network.SessionTokenStore
import app.nemla.core.network.createHttpClient
import app.nemla.core.network.createTokenProvider
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
import kotlin.test.assertIs
import kotlin.test.fail
import kotlin.time.Instant

internal const val API_BASE = "https://api.example.com"

internal val remoteConfig = BackendConfig("https://project.example.com", "sb_publishable_test", "$API_BASE/")

internal class StaticTokenStore : SessionTokenStore {
    var refreshCount = 0
    var invalidated = false

    override suspend fun currentAccessToken(): AccessToken = AccessToken("token-1", Instant.fromEpochSeconds(4_000_000_000))

    override suspend fun refreshAccessToken(): AppResult<AccessToken> {
        refreshCount++
        return AppResult.Success(AccessToken("token-2", Instant.fromEpochSeconds(4_000_000_000)))
    }

    override suspend fun invalidate() {
        invalidated = true
    }
}

internal class RecordedRequest(
    val data: HttpRequestData,
    val body: String,
)

internal class RemoteHarness(
    private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val requests = mutableListOf<RecordedRequest>()
    val tokens = StaticTokenStore()
    private val engine =
        MockEngine { request ->
            requests += RecordedRequest(request, request.body.toByteArray().decodeToString())
            handler(request)
        }
    val client = createHttpClient(remoteConfig, createTokenProvider(lazyOf(tokens)), engine = engine)
    val api = NemlaApi(client, remoteConfig)

    val single: RecordedRequest get() = requests.single()
}

internal fun MockRequestHandleScope.json(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

internal fun MockRequestHandleScope.html(status: HttpStatusCode): HttpResponseData =
    respond(
        "<html><body>Erreur jean@example.com</body></html>",
        status,
        headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
    )

internal fun speech(language: String = "fr-FR"): SpeechRequest =
    SpeechRequest(AudioClip(ByteArray(64) { it.toByte() }, AudioEncoding.WAV_PCM16, 16_000, 2_000), language)

internal suspend fun expectAiError(block: suspend () -> Unit): AiError {
    try {
        block()
    } catch (e: AiException) {
        return e.error
    }
    fail("Expected an AiException")
}

internal inline fun <reified T : AiError> AiError.expect(): T = assertIs<T>(this)
