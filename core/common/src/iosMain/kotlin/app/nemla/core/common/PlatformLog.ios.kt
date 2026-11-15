package app.nemla.core.common

import platform.Foundation.NSLog

internal actual fun writePlatformLog(
    level: LogLevel,
    line: String,
) {
    NSLog("%@", "$LOG_TAG [${level.name}] $line")
}
