package app.nemla.core.network

import app.nemla.core.common.AppLogger
import app.nemla.core.common.NoOpLogger
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

data class NetworkTimeouts(
    val requestMillis: Long = 90_000,
    val connectMillis: Long = 15_000,
    val socketMillis: Long = 60_000,
)

fun createHttpClient(
    config: BackendConfig,
    tokenProvider: TokenProvider,
    logger: AppLogger = NoOpLogger,
    enableRequestLog: Boolean = false,
    timeouts: NetworkTimeouts = NetworkTimeouts(),
    engine: HttpClientEngine? = null,
): HttpClient {
    val configure: HttpClientConfig<*>.() -> Unit = {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            requestTimeoutMillis = timeouts.requestMillis
            connectTimeoutMillis = timeouts.connectMillis
            socketTimeoutMillis = timeouts.socketMillis
        }
        install(ContentNegotiation) { json(NetworkJson) }
        install(HttpsOnly) { this.logger = logger }
        if (enableRequestLog) install(RequestLog) { this.logger = logger }
        install(NemlaAuth) {
            supabaseHost = config.supabaseHost
            apiHost = config.apiHost
            publishableKey = config.supabasePublishableKey
            this.tokenProvider = tokenProvider
        }
    }
    return HttpClient(engine ?: platformHttpEngine(), configure)
}

internal val NetworkJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

internal expect fun platformHttpEngine(): HttpClientEngine
