package app.nemla.core.network

import io.ktor.http.URLProtocol
import io.ktor.http.Url

data class BackendConfig(
    val supabaseUrl: String,
    val supabasePublishableKey: String,
    val apiBaseUrl: String,
) {
    val isComplete: Boolean
        get() = supabasePublishableKey.isNotBlank() && secureHostOf(supabaseUrl) != null && secureHostOf(apiBaseUrl) != null

    internal val supabaseHost: String? get() = secureHostOf(supabaseUrl)

    internal val apiHost: String? get() = secureHostOf(apiBaseUrl)

    override fun toString(): String = "BackendConfig(complete=$isComplete)"
}

internal fun secureHostOf(url: String): String? {
    val trimmed = url.trim()
    if (!trimmed.startsWith("https://", ignoreCase = true)) return null
    val parsed =
        try {
            Url(trimmed)
        } catch (e: IllegalArgumentException) {
            return null
        }
    return parsed.host.takeIf { parsed.protocol == URLProtocol.HTTPS && it.isNotBlank() }?.lowercase()
}
