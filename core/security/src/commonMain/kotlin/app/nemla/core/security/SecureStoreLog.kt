package app.nemla.core.security

import app.nemla.core.common.LogEvent

internal enum class SecureStoreLog : LogEvent {
    READ_FAILED,
    WRITE_FAILED,
    UNREADABLE_ENTRY_DISCARDED,
    CLEAR_FAILED,
}
