package app.nemla.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SilenceDetectorTest {
    @Test
    fun quietBlocksAccumulateUntilThreeSeconds() {
        val detector = SilenceDetector(sampleRateHz = 16_000)
        val quiet = FloatArray(4_096) { 0.009f }
        repeat(11) { detector.feed(quiet) }
        assertEquals(45_056, detector.silentSamples)
        assertFalse(detector.isLimitReached)
        detector.feed(quiet)
        assertTrue(detector.isLimitReached)
    }

    @Test
    fun aLoudBlockResetsTheCounter() {
        val detector = SilenceDetector(sampleRateHz = 16_000)
        repeat(5) { detector.feed(FloatArray(4_096)) }
        detector.feed(FloatArray(4_096) { 0.02f })
        assertEquals(0, detector.silentSamples)
    }

    @Test
    fun onlyCompleteBlocksAreEvaluated() {
        val detector = SilenceDetector(sampleRateHz = 16_000)
        detector.feed(FloatArray(4_000))
        assertEquals(0, detector.silentSamples)
        detector.feed(FloatArray(96))
        assertEquals(4_096, detector.silentSamples)
    }

    @Test
    fun rmsUsesTheWholeBlock() {
        val detector = SilenceDetector(sampleRateHz = 16_000, blockSize = 4)
        detector.feed(floatArrayOf(0.02f, 0f, 0f, 0f))
        assertEquals(4, detector.silentSamples)
        detector.feed(floatArrayOf(0.02f, 0.02f, 0f, 0f))
        assertEquals(0, detector.silentSamples)
    }
}
