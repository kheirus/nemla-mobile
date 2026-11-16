package app.nemla.core.audio

import kotlin.math.sqrt

internal class SilenceDetector(
    private val sampleRateHz: Int,
    private val blockSize: Int = RecordingRules.SILENCE_BLOCK_SAMPLES,
    private val threshold: Double = RecordingRules.SILENCE_RMS_THRESHOLD,
    private val silenceSeconds: Int = RecordingRules.SILENCE_SECONDS_BEFORE_STOP,
) {
    private var blockFill = 0
    private var blockEnergy = 0.0

    var silentSamples: Long = 0
        private set

    val isLimitReached: Boolean
        get() = silentSamples >= silenceSeconds.toLong() * sampleRateHz

    fun feed(
        samples: FloatArray,
        count: Int = samples.size,
    ) {
        for (index in 0 until count) {
            val sample = samples[index].toDouble()
            blockEnergy += sample * sample
            blockFill++
            if (blockFill == blockSize) closeBlock()
        }
    }

    private fun closeBlock() {
        val rms = sqrt(blockEnergy / blockSize)
        silentSamples = if (rms < threshold) silentSamples + blockSize else 0
        blockFill = 0
        blockEnergy = 0.0
    }
}
