package app.nemla.core.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive

@OptIn(ExperimentalForeignApi::class)
internal class IosMicrophone :
    AudioSource,
    MicrophonePermission {
    override fun status(): MicrophoneAccess =
        when (AVAudioSession.sharedInstance().recordPermission) {
            AVAudioSessionRecordPermissionGranted -> MicrophoneAccess.GRANTED
            AVAudioSessionRecordPermissionDenied -> MicrophoneAccess.DENIED
            else -> MicrophoneAccess.UNDETERMINED
        }

    override suspend fun open(): AudioInput {
        if (status() != MicrophoneAccess.GRANTED) throw MicrophoneException(RecordingError.PERMISSION_DENIED)
        val session = AVAudioSession.sharedInstance()
        val configured =
            session.setCategory(AVAudioSessionCategoryRecord, error = null) &&
                session.setActive(true, error = null)
        if (!configured) {
            deactivate(session)
            throw MicrophoneException(RecordingError.MICROPHONE_UNAVAILABLE)
        }
        val engine = AVAudioEngine()
        val input = engine.inputNode
        val format = input.outputFormatForBus(0u)
        val sampleRate = format.sampleRate.toInt()
        if (sampleRate <= 0 || format.channelCount == 0u) {
            deactivate(session)
            throw MicrophoneException(RecordingError.MICROPHONE_UNAVAILABLE)
        }
        val channel = Channel<FloatArray>(Channel.UNLIMITED)
        input.installTapOnBus(0u, TAP_BUFFER_FRAMES, format) { buffer, _ ->
            val samples = buffer?.floatChannelData?.get(0)
            val count = buffer?.frameLength?.toInt() ?: 0
            if (samples != null && count > 0) channel.trySend(FloatArray(count) { samples[it] })
        }
        engine.prepare()
        if (!engine.startAndReturnError(null)) {
            input.removeTapOnBus(0u)
            channel.close()
            deactivate(session)
            throw MicrophoneException(RecordingError.MICROPHONE_UNAVAILABLE)
        }
        return AudioInput(
            sampleRateHz = sampleRate,
            frames = channel.receiveAsFlow(),
            onClose = {
                input.removeTapOnBus(0u)
                engine.stop()
                channel.close()
                deactivate(session)
            },
        )
    }

    private fun deactivate(session: AVAudioSession) {
        session.setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)
    }

    private companion object {
        const val TAP_BUFFER_FRAMES: UInt = 4_096u
    }
}
