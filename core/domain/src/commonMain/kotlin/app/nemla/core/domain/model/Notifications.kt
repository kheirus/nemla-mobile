package app.nemla.core.domain.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth
import kotlin.jvm.JvmInline

enum class NotificationAccess { GRANTED, DENIED, UNDETERMINED }

sealed interface LocalNotice {
    data object DailyReminder : LocalNotice

    data class ReportArrived(
        val period: ReviewPeriod,
    ) : LocalNotice

    class BudgetReached(
        val categoryName: String,
        val usedCents: Long,
        val allocatedCents: Long,
        val currency: Currency,
    ) : LocalNotice {
        override fun equals(other: Any?): Boolean =
            other is BudgetReached &&
                other.categoryName == categoryName &&
                other.usedCents == usedCents &&
                other.allocatedCents == allocatedCents &&
                other.currency == currency

        override fun hashCode(): Int = listOf(categoryName, usedCents, allocatedCents, currency).hashCode()

        override fun toString(): String = "BudgetReached(***)"
    }

    class ServerMessage(
        val title: String,
        val body: String,
    ) : LocalNotice {
        override fun equals(other: Any?): Boolean = other is ServerMessage && other.title == title && other.body == body

        override fun hashCode(): Int = 31 * title.hashCode() + body.hashCode()

        override fun toString(): String = "ServerMessage(***)"
    }
}

data class ScheduledNotice(
    val notice: LocalNotice,
    val at: LocalDateTime,
)

enum class NoticeGroup { DAILY_REMINDER, REPORT_ARRIVAL }

class ReachedBudget(
    val categoryId: CategoryId,
    val categoryName: String,
    val usedCents: Long,
    val allocatedCents: Long,
) {
    val isReached: Boolean get() = allocatedCents > 0 && usedCents >= allocatedCents

    override fun equals(other: Any?): Boolean =
        other is ReachedBudget &&
            other.categoryId == categoryId &&
            other.categoryName == categoryName &&
            other.usedCents == usedCents &&
            other.allocatedCents == allocatedCents

    override fun hashCode(): Int = listOf(categoryId, categoryName, usedCents, allocatedCents).hashCode()

    override fun toString(): String = "ReachedBudget(categoryId=${categoryId.value}, ***)"
}

@JvmInline
value class InboxItemId(
    val value: String,
)

enum class InboxChannel { IN_APP, PUSH }

enum class InboxKind { MONTH_REPORT, YEAR_REPORT, DAILY_REMINDER }

class InboxItem(
    val id: InboxItemId,
    val channel: InboxChannel,
    val kind: InboxKind,
    val title: String,
    val body: String,
    val period: ReviewPeriod?,
    val report: ReportSheet?,
    val delivered: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        other is InboxItem &&
            other.id == id &&
            other.channel == channel &&
            other.kind == kind &&
            other.title == title &&
            other.body == body &&
            other.period == period &&
            other.report == report &&
            other.delivered == delivered

    override fun hashCode(): Int = listOf(id, channel, kind, title, body, period, report, delivered).hashCode()

    override fun toString(): String =
        "InboxItem(id=${id.value}, channel=$channel, kind=$kind, period=$period, report=${report != null}, delivered=$delivered)"
}

object NotificationPlanner {
    const val REMINDER_HOUR: Int = 20
    const val REMINDER_DAYS: Int = 14
    const val REPORT_HOUR: Int = 10
    const val REPORT_MONTHS: Int = 3

    fun reminders(
        now: LocalDateTime,
        confirmedToday: Boolean,
    ): List<ScheduledNotice> {
        val today = now.date
        val skipToday = confirmedToday || now >= today.atTime(REMINDER_HOUR, 0)
        return (0 until REMINDER_DAYS)
            .map { today.plus(DatePeriod(days = it)) }
            .filterNot { skipToday && it == today }
            .map { ScheduledNotice(LocalNotice.DailyReminder, it.atTime(LocalTime(REMINDER_HOUR, 0))) }
    }

    fun reportArrivals(now: LocalDateTime): List<ScheduledNotice> =
        generateSequence(now.date.yearMonth) { it.plusMonth() }
            .take(REPORT_MONTHS)
            .flatMap { month ->
                val at = arrivalDay(month).atTime(LocalTime(REPORT_HOUR, 0))
                val periods =
                    if (month.month == Month.DECEMBER) {
                        listOf(ReviewPeriod.Month(month), ReviewPeriod.Year(month.year))
                    } else {
                        listOf(ReviewPeriod.Month(month))
                    }
                periods.map { ScheduledNotice(LocalNotice.ReportArrived(it), at) }
            }.filter { it.at > now }
            .toList()

    fun arrivalDay(month: YearMonth): LocalDate = LocalDate(month.year, month.month, month.lastDay.day - (ReportCalendar.NOTICE_DAYS - 1))

    fun budgetKey(
        month: YearMonth,
        categoryId: CategoryId,
    ): String = "$month:${categoryId.value}"
}
