package app.nemla.core.audio

import app.nemla.core.common.LogEvent

internal enum class AudioLog : LogEvent {
    RECORDING_STARTED,
    RECORDING_FINISHED,
    RECORDING_FAILED,
}
