package app.nemla.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class WavEncoderTest {
    @Test
    fun headerDescribesMonoPcm16AtTheGivenRate() {
        val wav = WavEncoder.encode(FloatArray(10), 16_000)
        assertEquals(44 + 20, wav.size)
        assertEquals("RIFF", wav.ascii(0, 4))
        assertEquals(36 + 20, wav.intLe(4))
        assertEquals("WAVE", wav.ascii(8, 4))
        assertEquals("fmt ", wav.ascii(12, 4))
        assertEquals(16, wav.intLe(16))
        assertEquals(1, wav.shortLe(20))
        assertEquals(1, wav.shortLe(22))
        assertEquals(16_000, wav.intLe(24))
        assertEquals(32_000, wav.intLe(28))
        assertEquals(2, wav.shortLe(32))
        assertEquals(16, wav.shortLe(34))
        assertEquals("data", wav.ascii(36, 4))
        assertEquals(20, wav.intLe(40))
    }

    @Test
    fun samplesAreClampedAndScaledLikeTheWebEncoder() {
        val wav = WavEncoder.encode(floatArrayOf(1f, -1f, 0.5f, -0.5f, 2f, -3f, 0f, Float.NaN), 16_000)
        val values = (0 until 8).map { wav.shortLe(44 + it * 2) }
        assertEquals(listOf(32_767, -32_768, 16_383, -16_384, 32_767, -32_768, 0, 0), values)
    }

    @Test
    fun onlyTheRequestedLengthIsEncoded() {
        val wav = WavEncoder.encode(floatArrayOf(0.1f, 0.2f, 0.3f), 8_000, length = 2)
        assertEquals(48, wav.size)
        assertEquals(4, wav.intLe(40))
    }

    @Test
    fun emptyRecordingIsAHeaderOnly() {
        assertEquals(WavEncoder.HEADER_BYTES, WavEncoder.encode(FloatArray(0), 16_000).size)
    }
}
