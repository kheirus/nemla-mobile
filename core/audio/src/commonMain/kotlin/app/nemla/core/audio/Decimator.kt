package app.nemla.core.audio

import kotlin.math.floor
import kotlin.math.min

internal object Decimator {
    fun decimate(
        samples: FloatArray,
        length: Int,
        fromRateHz: Int,
        toRateHz: Int,
    ): FloatArray {
        if (fromRateHz <= toRateHz) return samples.copyOf(length)
        val ratio = fromRateHz.toDouble() / toRateHz
        val outLength = floor(length / ratio + 0.5).toInt()
        val last = length - 1
        return FloatArray(outLength) { index -> samples[min(floor(index * ratio).toInt(), last)] }
    }

    fun outputRate(
        fromRateHz: Int,
        toRateHz: Int,
    ): Int = min(fromRateHz, toRateHz)
}
