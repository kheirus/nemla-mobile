package app.nemla.core.audio

import kotlinx.coroutines.flow.Flow

internal interface AudioSource {
    suspend fun open(): AudioInput
}

internal class AudioInput(
    val sampleRateHz: Int,
    val frames: Flow<FloatArray>,
    private val onClose: () -> Unit,
) {
    private var closed = false

    fun close() {
        if (closed) return
        closed = true
        onClose()
    }
}

internal class MicrophoneException(
    val error: RecordingError,
) : Exception()
