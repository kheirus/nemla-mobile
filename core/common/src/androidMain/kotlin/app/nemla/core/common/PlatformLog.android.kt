package app.nemla.core.common

import android.util.Log

internal actual fun writePlatformLog(
    level: LogLevel,
    line: String,
) {
    val priority =
        when (level) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        }
    Log.println(priority, LOG_TAG, line)
}
