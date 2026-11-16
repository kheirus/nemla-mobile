package app.nemla.core.common

import kotlin.test.Test

class PlatformLogIosTest {
    @Test
    fun writesLinesContainingFormatSpecifiers() {
        writePlatformLog(LogLevel.DEBUG, "event=REQUEST status=200")
        writePlatformLog(LogLevel.WARN, "ratio=100% %@ %d %s %n")
    }
}
