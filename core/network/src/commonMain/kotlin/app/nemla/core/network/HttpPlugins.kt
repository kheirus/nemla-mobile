package app.nemla.core.network

import app.nemla.core.common.AppLogger
import app.nemla.core.common.LogAttribute
import app.nemla.core.common.LogField
import app.nemla.core.common.NoOpLogger
import app.nemla.core.common.debug
import app.nemla.core.common.getOrNull
import app.nemla.core.common.warn
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.util.AttributeKey
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

enum class AuthRequirement { USER_SESSION, NONE }

fun HttpRequestBuilder.authRequirement(requirement: AuthRequirement) {
    attributes.put(AuthRequirementKey, requirement)
}

fun HttpRequestBuilder.withoutUserSession() = authRequirement(AuthRequirement.NONE)

internal val AuthRequirementKey: AttributeKey<AuthRequirement> = AttributeKey("NemlaAuthRequirement")

internal const val SUPABASE_API_KEY_HEADER = "apikey"

internal class InsecureRequestException : IllegalStateException("Only HTTPS requests are allowed")

internal class HttpsOnlyConfig {
    var logger: AppLogger = NoOpLogger
}

internal val HttpsOnly =
    createClientPlugin("NemlaHttpsOnly", ::HttpsOnlyConfig) {
        val logger = pluginConfig.logger
        onRequest { request, _ ->
            if (request.url.protocol != URLProtocol.HTTPS) {
                logger.warn(NetworkLog.INSECURE_REQUEST_BLOCKED)
                throw InsecureRequestException()
            }
        }
    }

internal class NemlaAuthConfig {
    var supabaseHost: String? = null
    var apiHost: String? = null
    var publishableKey: String = ""
    var tokenProvider: TokenProvider? = null
}

internal val NemlaAuth =
    createClientPlugin("NemlaAuth", ::NemlaAuthConfig) {
        val supabaseHost = pluginConfig.supabaseHost
        val apiHost = pluginConfig.apiHost
        val publishableKey = pluginConfig.publishableKey
        val tokens = requireNotNull(pluginConfig.tokenProvider)

        on(Send) { request ->
            val host = request.url.host.lowercase()
            val isSupabase = supabaseHost != null && host == supabaseHost
            val isApi = apiHost != null && host == apiHost
            if (isSupabase) request.headers[SUPABASE_API_KEY_HEADER] = publishableKey
            val requirement = request.attributes.getOrNull(AuthRequirementKey) ?: AuthRequirement.USER_SESSION
            if (requirement == AuthRequirement.NONE || !(isSupabase || isApi)) return@on proceed(request)

            val token = tokens.accessToken() ?: return@on proceed(request)
            request.headers[HttpHeaders.Authorization] = "Bearer ${token.value}"
            val call = proceed(request)
            if (call.response.status != HttpStatusCode.Unauthorized) return@on call
            if (!isSessionRejection(call.response, fromSupabase = isSupabase)) return@on call

            val refreshed = tokens.refresh(token).getOrNull() ?: return@on call
            request.headers[HttpHeaders.Authorization] = "Bearer ${refreshed.value}"
            proceed(request)
        }
    }

internal class RequestLogConfig {
    var logger: AppLogger = NoOpLogger
}

internal val RequestLog =
    createClientPlugin("NemlaRequestLog", ::RequestLogConfig) {
        val logger = pluginConfig.logger
        on(Send) { request ->
            val service = serviceOf(request.url.build().encodedPath)
            val mark = TimeSource.Monotonic.markNow()
            try {
                val call = proceed(request)
                logger.debug(
                    NetworkLog.RESPONSE,
                    LogAttribute.choice(LogField.SERVICE, service),
                    LogAttribute.number(LogField.STATUS, call.response.status.value),
                    LogAttribute.number(LogField.DURATION_MS, mark.elapsedNow().inWholeMilliseconds),
                )
                call
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(
                    NetworkLog.REQUEST_FAILED,
                    LogAttribute.choice(LogField.SERVICE, service),
                    LogAttribute.errorType(e),
                    LogAttribute.number(LogField.DURATION_MS, mark.elapsedNow().inWholeMilliseconds),
                )
                throw e
            }
        }
    }
