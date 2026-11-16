package app.nemla.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PcmBufferTest {
    @Test
    fun growsAndStopsAtItsCapacity() {
        val buffer = PcmBuffer(maxSamples = 200_000)
        repeat(3) { buffer.append(FloatArray(80_000) { 0.25f }) }
        assertEquals(200_000, buffer.size)
        assertEquals(0.25f, buffer.samples[199_999])
    }

    @Test
    fun wipeErasesTheSamples() {
        val buffer = PcmBuffer(maxSamples = 10)
        buffer.append(floatArrayOf(0.5f, 0.5f))
        buffer.wipe()
        assertEquals(0, buffer.size)
        assertTrue(buffer.samples.all { it == 0f })
    }
}
