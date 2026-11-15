package app.nemla.core.network

import app.nemla.core.common.LogEvent

internal enum class NetworkLog : LogEvent {
    RESPONSE,
    REQUEST_FAILED,
    INSECURE_REQUEST_BLOCKED,
    SESSION_REFRESHED,
    SESSION_REFRESH_FAILED,
    SESSION_INVALIDATED,
}
