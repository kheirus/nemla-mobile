package app.nemla.core.network

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

suspend inline fun <reified T> safeRequest(noinline request: suspend () -> HttpResponse): AppResult<T> =
    executeSafely(request) { response -> response.body<T>() }

@PublishedApi
internal suspend fun <T> executeSafely(
    request: suspend () -> HttpResponse,
    decode: suspend (HttpResponse) -> T,
): AppResult<T> {
    val response =
        try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AppResult.Failure(e.toAppError())
        }
    if (!response.status.isSuccess()) return AppResult.Failure(response.toAppError())
    return try {
        AppResult.Success(decode(response))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(decodingError(e, response.status.value))
    }
}

enum class BackendService { AUTH, DATABASE, API, OTHER }

internal fun serviceOf(encodedPath: String): BackendService =
    when {
        encodedPath.contains("/auth/v1/") -> BackendService.AUTH
        encodedPath.contains("/rest/v1/") -> BackendService.DATABASE
        encodedPath.contains("/api/public/") -> BackendService.API
        else -> BackendService.OTHER
    }

suspend fun HttpResponse.toAppError(): AppError {
    val status = status.value
    val payload = readErrorPayload()
    return when (serviceOf(call.request.url.encodedPath)) {
        BackendService.AUTH -> authError(status, payload)
        BackendService.DATABASE -> databaseError(status, payload)
        BackendService.API -> apiError(status, payload?.text("error"))
        BackendService.OTHER -> statusError(status)
    }
}

fun Throwable.toAppError(): AppError =
    when (this) {
        is InsecureRequestException -> AppError.Configuration
        is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> AppError.Network.Timeout
        is SerializationException, is ContentConvertException, is NoTransformationFoundException -> AppError.Server(null)
        else -> platformNetworkError(this) ?: if (this is IOException) AppError.Network.Offline else AppError.Unknown
    }

internal expect fun platformNetworkError(error: Throwable): AppError?

internal suspend fun isSessionRejection(
    response: HttpResponse,
    fromSupabase: Boolean,
): Boolean {
    if (fromSupabase) return true
    val message = response.readErrorPayload()?.text("error") ?: return false
    return message in SESSION_REJECTION_MESSAGES
}

private val SESSION_REJECTION_MESSAGES = setOf("Session invalide", "Authorization Bearer token manquant")

private const val MAX_ERROR_BODY_CHARS = 16_384

private val errorJson = Json { ignoreUnknownKeys = true }

private suspend fun HttpResponse.readErrorPayload(): JsonObject? {
    val text =
        try {
            bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
    if (text.isBlank() || text.length > MAX_ERROR_BODY_CHARS) return null
    return try {
        errorJson.parseToJsonElement(text) as? JsonObject
    } catch (e: SerializationException) {
        null
    }
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun decodingError(
    error: Exception,
    status: Int,
): AppError =
    when (error) {
        is SerializationException, is ContentConvertException, is NoTransformationFoundException -> AppError.Server(status)
        else -> error.toAppError()
    }

private fun authError(
    status: Int,
    payload: JsonObject?,
): AppError {
    val code = payload?.text("error_code") ?: payload?.text("error")
    val description = payload?.text("error_description") ?: payload?.text("msg") ?: payload?.text("message")
    return when (code) {
        "invalid_credentials" -> {
            AppError.Validation(ValidationCode.INVALID_CREDENTIALS)
        }

        "email_not_confirmed" -> {
            AppError.Validation(ValidationCode.EMAIL_NOT_CONFIRMED)
        }

        "user_already_exists", "email_exists" -> {
            AppError.Validation(ValidationCode.EMAIL_ALREADY_REGISTERED)
        }

        "weak_password" -> {
            AppError.Validation(ValidationCode.PASSWORD_TOO_WEAK)
        }

        "email_address_invalid" -> {
            AppError.Validation(ValidationCode.EMAIL_INVALID)
        }

        "validation_failed", "bad_json" -> {
            AppError.Validation(ValidationCode.INVALID_INPUT)
        }

        "over_request_rate_limit", "over_email_send_rate_limit", "over_sms_send_rate_limit" -> {
            AppError.RateLimited
        }

        "signup_disabled", "email_provider_disabled", "user_banned" -> {
            AppError.Forbidden
        }

        "refresh_token_not_found", "refresh_token_already_used", "session_not_found", "session_expired",
        "bad_jwt", "no_authorization", "user_not_found",
        -> {
            AppError.Unauthorized
        }

        "invalid_grant" -> {
            when {
                description?.contains("Invalid login credentials", ignoreCase = true) == true -> {
                    AppError.Validation(ValidationCode.INVALID_CREDENTIALS)
                }

                description?.contains("Email not confirmed", ignoreCase = true) == true -> {
                    AppError.Validation(ValidationCode.EMAIL_NOT_CONFIRMED)
                }

                else -> {
                    AppError.Unauthorized
                }
            }
        }

        else -> {
            statusError(status)
        }
    }
}

private fun databaseError(
    status: Int,
    payload: JsonObject?,
): AppError {
    val code = payload?.text("code")
    return when (code) {
        "PGRST301", "PGRST302", "PGRST303" -> {
            AppError.Unauthorized
        }

        "PGRST116" -> {
            AppError.NotFound
        }

        "42501" -> {
            AppError.Forbidden
        }

        "23505" -> {
            AppError.Validation(ValidationCode.ALREADY_EXISTS)
        }

        "23502", "23503", "23514", "22001", "22003", "22P02", "PGRST102", "PGRST204" -> {
            AppError.Validation(ValidationCode.INVALID_INPUT)
        }

        "P0001" -> {
            if (payload.text("message") == "Non authentifié") AppError.Unauthorized else statusError(status)
        }

        else -> {
            statusError(status)
        }
    }
}

private fun apiError(
    status: Int,
    message: String?,
): AppError =
    when (status) {
        401 -> {
            if (message in SESSION_REJECTION_MESSAGES) AppError.Unauthorized else AppError.Server(status)
        }

        402 -> {
            AppError.Server(status)
        }

        429 -> {
            if (message?.startsWith("Quota") == true) AppError.QuotaExceeded else AppError.RateLimited
        }

        413 -> {
            AppError.Validation(ValidationCode.PAYLOAD_TOO_LARGE)
        }

        422 -> {
            AppError.Validation(
                when {
                    message == null -> ValidationCode.INVALID_INPUT
                    message.startsWith("Aucune parole") -> ValidationCode.NO_SPEECH_DETECTED
                    message.startsWith("Aucune dépense") -> ValidationCode.NO_EXPENSE_DETECTED
                    message.startsWith("Impossible de lire") -> ValidationCode.UNREADABLE_FILE
                    message.startsWith("Ce fichier couvre") -> ValidationCode.PERIOD_TOO_LONG
                    else -> ValidationCode.INVALID_INPUT
                },
            )
        }

        400 -> {
            AppError.Validation(
                if (message?.startsWith("Aucune catégorie") == true) ValidationCode.NO_CATEGORY else ValidationCode.INVALID_INPUT,
            )
        }

        else -> {
            statusError(status)
        }
    }

internal fun statusError(status: Int): AppError =
    when (status) {
        400, 422 -> AppError.Validation(ValidationCode.INVALID_INPUT)
        401 -> AppError.Unauthorized
        403 -> AppError.Forbidden
        404 -> AppError.NotFound
        408 -> AppError.Network.Timeout
        409 -> AppError.Validation(ValidationCode.ALREADY_EXISTS)
        413 -> AppError.Validation(ValidationCode.PAYLOAD_TOO_LARGE)
        429 -> AppError.RateLimited
        in 500..599 -> AppError.Server(status)
        else -> AppError.Unknown
    }
