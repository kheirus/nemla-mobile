package app.nemla.core.audio

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

internal fun ByteArray.intLe(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)

internal fun ByteArray.shortLe(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)).toShort().toInt()

internal fun ByteArray.ascii(
    offset: Int,
    length: Int,
): String = (offset until offset + length).map { this[it].toInt().toChar() }.joinToString("")

internal class FakePermission(
    var access: MicrophoneAccess = MicrophoneAccess.GRANTED,
) : MicrophonePermission {
    override fun status(): MicrophoneAccess = access
}

internal class FakeAudioSource(
    private val sampleRateHz: Int = 48_000,
    private val chunkMillis: Long = 100,
    private val openError: RecordingError? = null,
    private val level: (elapsedMillis: Long) -> Float = { 0.5f },
) : AudioSource {
    var opened = 0
    var closed = 0

    override suspend fun open(): AudioInput {
        openError?.let { throw MicrophoneException(it) }
        opened++
        val chunkSamples = (sampleRateHz * chunkMillis / 1_000).toInt()
        val frames =
            flow {
                var elapsed = 0L
                while (true) {
                    delay(chunkMillis)
                    val amplitude = level(elapsed)
                    emit(FloatArray(chunkSamples) { if (it % 2 == 0) amplitude else -amplitude })
                    elapsed += chunkMillis
                }
            }
        return AudioInput(sampleRateHz, frames) { closed++ }
    }
}
