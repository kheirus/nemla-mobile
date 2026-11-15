package app.nemla.core.common

interface LogEvent

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

enum class LogField {
    STATUS,
    COUNT,
    DURATION_MS,
    ATTEMPT,
    SERVICE,
    METHOD,
    KIND,
    REASON,
    ERROR_TYPE,
    ENABLED,
}

class LogAttribute private constructor(
    val field: LogField,
    internal val rendered: String,
) {
    override fun toString(): String = "${field.name.lowercase()}=$rendered"

    companion object {
        fun number(
            field: LogField,
            value: Long,
        ): LogAttribute = LogAttribute(field, value.toString())

        fun number(
            field: LogField,
            value: Int,
        ): LogAttribute = LogAttribute(field, value.toString())

        fun flag(
            field: LogField,
            value: Boolean,
        ): LogAttribute = LogAttribute(field, value.toString())

        fun choice(
            field: LogField,
            value: Enum<*>,
        ): LogAttribute = LogAttribute(field, value.name)

        fun errorType(error: Throwable): LogAttribute = LogAttribute(LogField.ERROR_TYPE, error::class.simpleName ?: "Throwable")
    }
}

interface AppLogger {
    fun <E> log(
        level: LogLevel,
        event: E,
        attributes: List<LogAttribute>,
    ) where E : Enum<E>, E : LogEvent
}

fun <E> AppLogger.debug(
    event: E,
    vararg attributes: LogAttribute,
) where E : Enum<E>, E : LogEvent = log(LogLevel.DEBUG, event, attributes.asList())

fun <E> AppLogger.info(
    event: E,
    vararg attributes: LogAttribute,
) where E : Enum<E>, E : LogEvent = log(LogLevel.INFO, event, attributes.asList())

fun <E> AppLogger.warn(
    event: E,
    vararg attributes: LogAttribute,
) where E : Enum<E>, E : LogEvent = log(LogLevel.WARN, event, attributes.asList())

fun <E> AppLogger.error(
    event: E,
    vararg attributes: LogAttribute,
) where E : Enum<E>, E : LogEvent = log(LogLevel.ERROR, event, attributes.asList())

object NoOpLogger : AppLogger {
    override fun <E> log(
        level: LogLevel,
        event: E,
        attributes: List<LogAttribute>,
    ) where E : Enum<E>, E : LogEvent = Unit
}

fun platformLogger(buildInfo: AppBuildInfo): AppLogger = if (buildInfo.isDebug) PlatformLogger else NoOpLogger

internal object PlatformLogger : AppLogger {
    override fun <E> log(
        level: LogLevel,
        event: E,
        attributes: List<LogAttribute>,
    ) where E : Enum<E>, E : LogEvent {
        writePlatformLog(level, formatLogLine(event, attributes))
    }
}

internal fun <E> formatLogLine(
    event: E,
    attributes: List<LogAttribute>,
): String where E : Enum<E>, E : LogEvent {
    val scope = event::class.simpleName ?: "Event"
    val suffix = if (attributes.isEmpty()) "" else attributes.joinToString(separator = " ", prefix = " ")
    return "$scope.${event.name}$suffix"
}

internal const val LOG_TAG = "nemla"

internal expect fun writePlatformLog(
    level: LogLevel,
    line: String,
)
