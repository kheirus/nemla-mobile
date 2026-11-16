package app.nemla.core.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

enum class RecordingError { PERMISSION_DENIED, TOO_SHORT, MICROPHONE_UNAVAILABLE }

sealed interface RecorderState {
    data object Idle : RecorderState

    data class Recording(
        val elapsedSeconds: Int,
        val maxSeconds: Int = RecordingRules.MAX_SECONDS,
    ) : RecorderState

    class Finished(
        val wavBytes: ByteArray,
        val sampleRateHz: Int,
        val durationMillis: Long,
    ) : RecorderState {
        override fun toString(): String = "Finished(${wavBytes.size} bytes, ${durationMillis}ms)"
    }

    data class Failed(
        val error: RecordingError,
    ) : RecorderState
}

interface VoiceRecorder {
    val state: StateFlow<RecorderState>

    fun start()

    fun stop()

    fun cancel()
}

fun interface VoiceRecorderFactory {
    fun create(scope: CoroutineScope): VoiceRecorder
}

enum class MicrophoneAccess { GRANTED, DENIED, UNDETERMINED }

interface MicrophonePermission {
    fun status(): MicrophoneAccess
}
