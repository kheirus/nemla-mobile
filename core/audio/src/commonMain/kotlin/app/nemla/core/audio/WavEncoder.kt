package app.nemla.core.audio

internal object WavEncoder {
    const val HEADER_BYTES: Int = 44
    private const val BYTES_PER_SAMPLE = 2
    private const val BITS_PER_SAMPLE = 16
    private const val PCM_FORMAT = 1
    private const val MONO = 1
    private const val FMT_CHUNK_BYTES = 16
    private const val RIFF_HEADER_REMAINDER = 36

    fun encode(
        samples: FloatArray,
        sampleRateHz: Int,
        length: Int = samples.size,
    ): ByteArray {
        val dataBytes = length * BYTES_PER_SAMPLE
        val out = ByteArray(HEADER_BYTES + dataBytes)
        out.writeAscii(0, "RIFF")
        out.writeIntLe(4, RIFF_HEADER_REMAINDER + dataBytes)
        out.writeAscii(8, "WAVE")
        out.writeAscii(12, "fmt ")
        out.writeIntLe(16, FMT_CHUNK_BYTES)
        out.writeShortLe(20, PCM_FORMAT)
        out.writeShortLe(22, MONO)
        out.writeIntLe(24, sampleRateHz)
        out.writeIntLe(28, sampleRateHz * MONO * BYTES_PER_SAMPLE)
        out.writeShortLe(32, MONO * BYTES_PER_SAMPLE)
        out.writeShortLe(34, BITS_PER_SAMPLE)
        out.writeAscii(36, "data")
        out.writeIntLe(40, dataBytes)
        for (index in 0 until length) {
            out.writeShortLe(HEADER_BYTES + index * BYTES_PER_SAMPLE, toPcm16(samples[index]))
        }
        return out
    }

    fun toPcm16(sample: Float): Int {
        val clamped = sample.coerceIn(-1f, 1f)
        if (clamped.isNaN()) return 0
        return if (clamped < 0f) (clamped * 32_768f).toInt() else (clamped * 32_767f).toInt()
    }

    private fun ByteArray.writeAscii(
        offset: Int,
        text: String,
    ) {
        text.forEachIndexed { index, char -> this[offset + index] = char.code.toByte() }
    }

    private fun ByteArray.writeIntLe(
        offset: Int,
        value: Int,
    ) {
        this[offset] = value.toByte()
        this[offset + 1] = (value shr 8).toByte()
        this[offset + 2] = (value shr 16).toByte()
        this[offset + 3] = (value shr 24).toByte()
    }

    private fun ByteArray.writeShortLe(
        offset: Int,
        value: Int,
    ) {
        this[offset] = value.toByte()
        this[offset + 1] = (value shr 8).toByte()
    }
}
