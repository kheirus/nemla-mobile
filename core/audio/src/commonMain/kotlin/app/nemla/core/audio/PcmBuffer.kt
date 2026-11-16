package app.nemla.core.audio

import kotlin.math.max
import kotlin.math.min

internal class PcmBuffer(
    private val maxSamples: Int,
) {
    private var data = FloatArray(min(maxSamples, INITIAL_CAPACITY))

    var size: Int = 0
        private set

    val samples: FloatArray get() = data

    fun append(
        chunk: FloatArray,
        count: Int = chunk.size,
    ) {
        val accepted = min(count, maxSamples - size)
        if (accepted <= 0) return
        ensureCapacity(size + accepted)
        chunk.copyInto(data, destinationOffset = size, startIndex = 0, endIndex = accepted)
        size += accepted
    }

    fun wipe() {
        data.fill(0f)
        size = 0
    }

    private fun ensureCapacity(required: Int) {
        if (required <= data.size) return
        val grown = min(maxSamples, max(required, data.size * 2))
        val next = data.copyOf(grown)
        data.fill(0f)
        data = next
    }

    private companion object {
        const val INITIAL_CAPACITY = 64 * 1_024
    }
}
