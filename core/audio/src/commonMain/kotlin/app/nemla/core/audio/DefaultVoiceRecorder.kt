package app.nemla.core.audio

import app.nemla.core.common.AppLogger
import app.nemla.core.common.LogAttribute
import app.nemla.core.common.LogField
import app.nemla.core.common.info
import app.nemla.core.common.warn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

internal class DefaultVoiceRecorderFactory(
    private val source: AudioSource,
    private val permission: MicrophonePermission,
    private val encoding: CoroutineDispatcher,
    private val logger: AppLogger,
) : VoiceRecorderFactory {
    override fun create(scope: CoroutineScope): VoiceRecorder = DefaultVoiceRecorder(scope, source, permission, encoding, logger)
}

internal class DefaultVoiceRecorder(
    private val scope: CoroutineScope,
    private val source: AudioSource,
    private val permission: MicrophonePermission,
    private val encoding: CoroutineDispatcher,
    private val logger: AppLogger,
) : VoiceRecorder {
    private val mutableState = MutableStateFlow<RecorderState>(RecorderState.Idle)
    override val state: StateFlow<RecorderState> = mutableState.asStateFlow()

    private var job: Job? = null
    private var stopRequest: CompletableDeferred<Unit>? = null

    override fun start() {
        if (job?.isActive == true) return
        val stop = CompletableDeferred<Unit>()
        stopRequest = stop
        mutableState.value = RecorderState.Recording(elapsedSeconds = 0)
        job = scope.launch { record(stop) }
    }

    override fun stop() {
        stopRequest?.complete(Unit)
    }

    override fun cancel() {
        job?.cancel()
        job = null
        stopRequest = null
        mutableState.value = RecorderState.Idle
    }

    private suspend fun record(stop: CompletableDeferred<Unit>) {
        if (permission.status() != MicrophoneAccess.GRANTED) return fail(RecordingError.PERMISSION_DENIED)
        val input =
            try {
                source.open()
            } catch (e: CancellationException) {
                throw e
            } catch (e: MicrophoneException) {
                return fail(e.error)
            } catch (e: Exception) {
                return fail(RecordingError.MICROPHONE_UNAVAILABLE)
            }
        logger.info(AudioLog.RECORDING_STARTED)
        val buffer = PcmBuffer(maxSamples = input.sampleRateHz * (RecordingRules.MAX_SECONDS + 1))
        try {
            val captured = capture(input, buffer, stop)
            if (!captured) return fail(RecordingError.MICROPHONE_UNAVAILABLE)
            input.close()
            val result = withContext(encoding) { encodeRecording(buffer.samples, buffer.size, input.sampleRateHz) }
            mutableState.value = result
            if (result is RecorderState.Finished) {
                logger.info(AudioLog.RECORDING_FINISHED, LogAttribute.number(LogField.DURATION_MS, result.durationMillis))
            } else if (result is RecorderState.Failed) {
                logger.warn(AudioLog.RECORDING_FAILED, LogAttribute.choice(LogField.REASON, result.error))
            }
        } finally {
            input.close()
            buffer.wipe()
        }
    }

    private suspend fun capture(
        input: AudioInput,
        buffer: PcmBuffer,
        stop: CompletableDeferred<Unit>,
    ): Boolean {
        val silence = SilenceDetector(input.sampleRateHz)
        return try {
            coroutineScope {
                val frames =
                    launch {
                        input.frames.collect { chunk ->
                            buffer.append(chunk)
                            silence.feed(chunk)
                        }
                    }
                var ticks = 0
                while (true) {
                    val stopped = withTimeoutOrNull(RecordingRules.CHECK_INTERVAL_MILLIS) { stop.await() } != null
                    if (stopped || frames.isCompleted) break
                    ticks++
                    if (ticks % TICKS_PER_SECOND == 0) {
                        val seconds = ticks / TICKS_PER_SECOND
                        mutableState.value = RecorderState.Recording(elapsedSeconds = seconds)
                        if (seconds >= RecordingRules.MAX_SECONDS) break
                    }
                    if (silence.isLimitReached) break
                }
                frames.cancel()
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private fun fail(error: RecordingError) {
        logger.warn(AudioLog.RECORDING_FAILED, LogAttribute.choice(LogField.REASON, error))
        mutableState.value = RecorderState.Failed(error)
    }

    private companion object {
        val TICKS_PER_SECOND = (1_000 / RecordingRules.CHECK_INTERVAL_MILLIS).toInt()
    }
}

internal fun encodeRecording(
    samples: FloatArray,
    length: Int,
    sampleRateHz: Int,
): RecorderState {
    val outputRate = Decimator.outputRate(sampleRateHz, RecordingRules.TARGET_SAMPLE_RATE_HZ)
    val pcm = Decimator.decimate(samples, length, sampleRateHz, RecordingRules.TARGET_SAMPLE_RATE_HZ)
    val wav = WavEncoder.encode(pcm, outputRate)
    pcm.fill(0f)
    if (wav.size < RecordingRules.MIN_WAV_BYTES) return RecorderState.Failed(RecordingError.TOO_SHORT)
    val durationMillis = if (sampleRateHz > 0) length * 1_000L / sampleRateHz else 0L
    return RecorderState.Finished(wav, outputRate, durationMillis)
}
