package app.nemla.core.audio

import app.nemla.core.common.NoOpLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceRecorderTest {
    private fun TestScope.recorder(
        source: FakeAudioSource,
        permission: FakePermission = FakePermission(),
    ): VoiceRecorder = DefaultVoiceRecorderFactory(source, permission, StandardTestDispatcher(testScheduler), NoOpLogger).create(this)

    private fun RecorderState.finished(): RecorderState.Finished = assertIs<RecorderState.Finished>(this)

    @Test
    fun stoppingProducesSixteenKilohertzMonoWav() =
        runTest {
            val source = FakeAudioSource(sampleRateHz = 48_000)
            val recorder = recorder(source)
            recorder.start()
            advanceTimeBy(2_050)
            recorder.stop()
            advanceUntilIdle()
            val result = recorder.state.value.finished()
            assertEquals(16_000, result.sampleRateHz)
            assertEquals(16_000, result.wavBytes.intLe(24))
            assertEquals(2_000, result.durationMillis)
            assertEquals(44 + 32_000 * 2, result.wavBytes.size)
            assertEquals(1, source.closed)
        }

    @Test
    fun elapsedSecondsCountUp() =
        runTest {
            val recorder = recorder(FakeAudioSource())
            recorder.start()
            runCurrent()
            assertEquals(RecorderState.Recording(0), recorder.state.value)
            advanceTimeBy(1_100)
            assertEquals(RecorderState.Recording(1), recorder.state.value)
            advanceTimeBy(1_000)
            assertEquals(RecorderState.Recording(2, maxSeconds = 30), recorder.state.value)
            recorder.cancel()
        }

    @Test
    fun recordingStopsByItselfAfterThirtySeconds() =
        runTest {
            val recorder = recorder(FakeAudioSource())
            recorder.start()
            advanceTimeBy(45_000)
            advanceUntilIdle()
            val duration =
                recorder.state.value
                    .finished()
                    .durationMillis
            assertTrue(duration in 29_900..30_000, "duration=$duration")
        }

    @Test
    fun threeSecondsOfSilenceAtTheStartStopTheRecording() =
        runTest {
            val recorder = recorder(FakeAudioSource(sampleRateHz = 16_000, level = { 0f }))
            recorder.start()
            advanceTimeBy(10_000)
            advanceUntilIdle()
            val duration =
                recorder.state.value
                    .finished()
                    .durationMillis
            assertTrue(duration in 3_000..3_600, "duration=$duration")
        }

    @Test
    fun speechResetsTheSilenceCounter() =
        runTest {
            val recorder = recorder(FakeAudioSource(sampleRateHz = 16_000, level = { if (it < 2_000) 0.3f else 0.001f }))
            recorder.start()
            advanceTimeBy(20_000)
            advanceUntilIdle()
            val duration =
                recorder.state.value
                    .finished()
                    .durationMillis
            assertTrue(duration in 5_000..5_600, "duration=$duration")
        }

    @Test
    fun aTinyRecordingIsRejected() =
        runTest {
            val recorder = recorder(FakeAudioSource())
            recorder.start()
            advanceTimeBy(50)
            recorder.stop()
            advanceUntilIdle()
            assertEquals(RecorderState.Failed(RecordingError.TOO_SHORT), recorder.state.value)
        }

    @Test
    fun missingPermissionFailsWithoutOpeningTheMicrophone() =
        runTest {
            val source = FakeAudioSource()
            val recorder = recorder(source, FakePermission(MicrophoneAccess.DENIED))
            recorder.start()
            advanceUntilIdle()
            assertEquals(RecorderState.Failed(RecordingError.PERMISSION_DENIED), recorder.state.value)
            assertEquals(0, source.opened)
        }

    @Test
    fun unavailableMicrophoneIsReported() =
        runTest {
            val recorder = recorder(FakeAudioSource(openError = RecordingError.MICROPHONE_UNAVAILABLE))
            recorder.start()
            advanceUntilIdle()
            assertEquals(RecorderState.Failed(RecordingError.MICROPHONE_UNAVAILABLE), recorder.state.value)
        }

    @Test
    fun cancelReleasesTheMicrophoneAndKeepsNothing() =
        runTest {
            val source = FakeAudioSource()
            val recorder = recorder(source)
            recorder.start()
            advanceTimeBy(1_500)
            recorder.cancel()
            advanceUntilIdle()
            assertEquals(RecorderState.Idle, recorder.state.value)
            assertEquals(1, source.closed)
        }

    @Test
    fun startingTwiceKeepsASingleRecording() =
        runTest {
            val source = FakeAudioSource()
            val recorder = recorder(source)
            recorder.start()
            runCurrent()
            recorder.start()
            advanceTimeBy(1_000)
            assertEquals(1, source.opened)
            recorder.cancel()
        }

    @Test
    fun aNewRecordingCanFollowAFinishedOne() =
        runTest {
            val source = FakeAudioSource()
            val recorder = recorder(source)
            recorder.start()
            advanceTimeBy(1_000)
            recorder.stop()
            advanceUntilIdle()
            recorder.state.value.finished()
            recorder.start()
            runCurrent()
            assertEquals(RecorderState.Recording(0), recorder.state.value)
            assertEquals(2, source.opened)
            recorder.cancel()
        }
}
