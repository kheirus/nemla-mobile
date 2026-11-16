package app.nemla.core.data.remote

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.data.session.SessionManager
import app.nemla.core.network.BackendConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

internal class Postgrest(
    val client: HttpClient,
    private val config: BackendConfig,
    private val sessions: SessionManager,
) {
    fun table(name: String): String = base() + "/rest/v1/" + name

    fun rpc(name: String): String = base() + "/rest/v1/rpc/" + name

    suspend fun <T> withUser(block: suspend (String) -> AppResult<T>): AppResult<T> {
        val userId = sessions.userId() ?: return AppResult.Failure(AppError.Unauthorized)
        return block(userId)
    }

    private fun base(): String = config.supabaseUrl.trim().trimEnd('/')
}

internal inline fun <reified T : Any> HttpRequestBuilder.jsonBody(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

internal fun HttpRequestBuilder.returnRepresentation() {
    header(PREFER_HEADER, "return=representation")
}

internal fun eq(value: String): String = "eq.$value"

internal const val PREFER_HEADER = "Prefer"

private val decimalPattern = Regex("^([+-])?([0-9]+)(?:\\.([0-9]*))?(?:[eE]([+-]?[0-9]{1,3}))?$")

private const val MAX_CENT_DIGITS = 17

internal fun decimalToCents(raw: String): Long? {
    val match = decimalPattern.matchEntire(raw.trim()) ?: return null
    val (sign, integer, fraction, exponent) = match.destructured
    val digits = (integer + fraction).trimStart('0').ifEmpty { "0" }
    val shift = 2 - fraction.length + (exponent.toIntOrNull() ?: 0)
    val magnitude =
        if (shift >= 0) {
            if (digits.length + shift > MAX_CENT_DIGITS) return null
            (digits + "0".repeat(shift)).toLong()
        } else {
            val padded = digits.padStart(-shift + 1, '0')
            val kept = padded.dropLast(-shift)
            if (kept.length > MAX_CENT_DIGITS) return null
            val roundUp = padded[padded.length + shift] >= '5'
            kept.toLong() + if (roundUp) 1 else 0
        }
    return if (sign == "-") -magnitude else magnitude
}

internal fun JsonElement?.toCents(): Long? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.let(::decimalToCents)

@OptIn(ExperimentalSerializationApi::class)
internal fun centsToJson(cents: Long?): JsonElement {
    if (cents == null) return JsonNull
    val negative = cents < 0
    val magnitude = if (negative) -cents else cents
    val units = magnitude / 100
    val fraction = (magnitude % 100).toString().padStart(2, '0')
    return JsonUnquotedLiteral((if (negative) "-" else "") + "$units.$fraction")
}
