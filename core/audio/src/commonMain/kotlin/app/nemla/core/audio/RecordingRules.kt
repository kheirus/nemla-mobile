package app.nemla.core.audio

object RecordingRules {
    const val MAX_SECONDS: Int = 30
    const val TARGET_SAMPLE_RATE_HZ: Int = 16_000
    const val MIN_WAV_BYTES: Int = 4_096
    const val SILENCE_BLOCK_SAMPLES: Int = 4_096
    const val SILENCE_RMS_THRESHOLD: Double = 0.01
    const val SILENCE_SECONDS_BEFORE_STOP: Int = 3
    const val CHECK_INTERVAL_MILLIS: Long = 500
}
