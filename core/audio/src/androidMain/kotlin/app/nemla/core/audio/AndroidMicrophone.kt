package app.nemla.core.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

internal class AndroidMicrophone(
    private val context: Context,
    private val io: CoroutineDispatcher,
) : AudioSource,
    MicrophonePermission {
    override fun status(): MicrophoneAccess =
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            MicrophoneAccess.GRANTED
        } else {
            MicrophoneAccess.UNDETERMINED
        }

    override suspend fun open(): AudioInput =
        withContext(io) {
            if (status() != MicrophoneAccess.GRANTED) throw MicrophoneException(RecordingError.PERMISSION_DENIED)
            val record = createRecord() ?: throw MicrophoneException(RecordingError.MICROPHONE_UNAVAILABLE)
            val handle = RecordHandle(record)
            try {
                record.startRecording()
            } catch (e: IllegalStateException) {
                handle.close()
                throw MicrophoneException(RecordingError.MICROPHONE_UNAVAILABLE)
            }
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                handle.close()
                throw MicrophoneException(RecordingError.MICROPHONE_UNAVAILABLE)
            }
            AudioInput(sampleRateHz = record.sampleRate, frames = frames(handle), onClose = handle::close)
        }

    private fun frames(handle: RecordHandle) =
        flow {
            if (!handle.beginRead()) return@flow
            try {
                val shorts = ShortArray(CHUNK_SAMPLES)
                while (currentCoroutineContext().isActive) {
                    val read = handle.record.read(shorts, 0, shorts.size)
                    when {
                        read < 0 -> throw MicrophoneException(RecordingError.MICROPHONE_UNAVAILABLE)
                        read > 0 -> emit(FloatArray(read) { shorts[it] / PCM16_SCALE })
                        handle.record.recordingState != AudioRecord.RECORDSTATE_RECORDING -> break
                    }
                }
                shorts.fill(0)
            } finally {
                handle.endRead()
            }
        }.flowOn(io)

    @SuppressLint("MissingPermission")
    private fun createRecord(): AudioRecord? {
        for (rate in CANDIDATE_RATES) {
            val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) continue
            for (source in CANDIDATE_SOURCES) {
                val record =
                    try {
                        AudioRecord(
                            source,
                            rate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            max(minBuffer, CHUNK_SAMPLES * BYTES_PER_SAMPLE * 2),
                        )
                    } catch (e: IllegalArgumentException) {
                        null
                    } catch (e: SecurityException) {
                        throw MicrophoneException(RecordingError.PERMISSION_DENIED)
                    }
                if (record?.state == AudioRecord.STATE_INITIALIZED) return record
                record?.release()
            }
        }
        return null
    }

    private class RecordHandle(
        val record: AudioRecord,
    ) {
        private val phase = AtomicInteger(OPEN)

        fun beginRead(): Boolean = phase.compareAndSet(OPEN, READING)

        fun endRead() {
            stopQuietly()
            if (phase.getAndSet(CLOSED) != CLOSED) record.release()
        }

        fun close() {
            stopQuietly()
            if (phase.compareAndSet(OPEN, CLOSED)) record.release()
        }

        private fun stopQuietly() {
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            } catch (e: IllegalStateException) {
                return
            }
        }

        private companion object {
            const val OPEN = 0
            const val READING = 1
            const val CLOSED = 2
        }
    }

    private companion object {
        const val CHUNK_SAMPLES = 4_096
        const val BYTES_PER_SAMPLE = 2
        const val PCM16_SCALE = 32_768f
        val CANDIDATE_RATES = intArrayOf(RecordingRules.TARGET_SAMPLE_RATE_HZ, 48_000, 44_100)
        val CANDIDATE_SOURCES = intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
    }
}
