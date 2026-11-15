package app.nemla.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AppLoggerTest {
    private enum class SampleEvent : LogEvent { REQUEST_DONE }

    @Test
    fun formatsEventWithTypedAttributes() {
        val line =
            formatLogLine(
                SampleEvent.REQUEST_DONE,
                listOf(
                    LogAttribute.number(LogField.STATUS, 200),
                    LogAttribute.flag(LogField.ENABLED, true),
                    LogAttribute.choice(LogField.KIND, LogLevel.WARN),
                    LogAttribute.errorType(IllegalStateException("secret@example.com")),
                ),
            )
        assertEquals("SampleEvent.REQUEST_DONE status=200 enabled=true kind=WARN error_type=IllegalStateException", line)
    }

    @Test
    fun errorTypeNeverContainsTheMessage() {
        val attribute = LogAttribute.errorType(IllegalArgumentException("4111 1111 1111 1111"))
        assertEquals("error_type=IllegalArgumentException", attribute.toString())
    }

    @Test
    fun releaseBuildsUseNoOpLogger() {
        assertSame(NoOpLogger, platformLogger(AppBuildInfo(isDebug = false)))
    }

    @Test
    fun recordingLoggerReceivesEvents() {
        val recorded = mutableListOf<String>()
        val logger =
            object : AppLogger {
                override fun <E> log(
                    level: LogLevel,
                    event: E,
                    attributes: List<LogAttribute>,
                ) where E : Enum<E>, E : LogEvent {
                    recorded += "${level.name} ${formatLogLine(event, attributes)}"
                }
            }
        logger.info(SampleEvent.REQUEST_DONE, LogAttribute.number(LogField.COUNT, 3L))
        assertEquals(listOf("INFO SampleEvent.REQUEST_DONE count=3"), recorded)
    }
}
