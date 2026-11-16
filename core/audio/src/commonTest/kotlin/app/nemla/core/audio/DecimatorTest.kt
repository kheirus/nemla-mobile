package app.nemla.core.audio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class DecimatorTest {
    @Test
    fun fortyEightKilohertzKeepsEveryThirdSample() {
        val input = FloatArray(9) { it.toFloat() }
        assertContentEquals(floatArrayOf(0f, 3f, 6f), Decimator.decimate(input, input.size, 48_000, 16_000))
    }

    @Test
    fun nonIntegerRatioUsesTheNearestPreviousSampleWithoutFiltering() {
        val input = FloatArray(12) { it.toFloat() }
        val output = Decimator.decimate(input, input.size, 44_100, 16_000)
        assertEquals(4, output.size)
        assertContentEquals(floatArrayOf(0f, 2f, 5f, 8f), output)
    }

    @Test
    fun outputLengthIsRoundedHalfUpLikeMathRound() {
        val input = FloatArray(15)
        assertEquals(5, Decimator.decimate(input, 15, 48_000, 16_000).size)
        assertEquals(1, Decimator.decimate(FloatArray(2), 2, 64_000, 16_000).size)
        assertEquals(1, Decimator.decimate(FloatArray(5), 5, 64_000, 16_000).size)
        assertEquals(2, Decimator.decimate(FloatArray(6), 6, 64_000, 16_000).size)
        assertEquals(2, Decimator.decimate(FloatArray(7), 7, 64_000, 16_000).size)
    }

    @Test
    fun targetRateOrLowerIsCopiedUnchanged() {
        val input = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        assertContentEquals(floatArrayOf(0.1f, 0.2f, 0.3f), Decimator.decimate(input, 3, 16_000, 16_000))
        assertContentEquals(input, Decimator.decimate(input, 4, 8_000, 16_000))
        assertEquals(8_000, Decimator.outputRate(8_000, 16_000))
        assertEquals(16_000, Decimator.outputRate(48_000, 16_000))
    }
}
