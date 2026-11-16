package app.nemla.core.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.number
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth

enum class PeriodKind { MONTH, YEAR }

data class DateSpan(
    val from: LocalDate,
    val to: LocalDate,
) {
    operator fun contains(date: LocalDate): Boolean = date in from..to
}

data class PeriodCursor(
    val kind: PeriodKind,
    val month: YearMonth,
) {
    val year: Int get() = month.year

    val monthNumber: Int get() = month.month.number

    val span: DateSpan
        get() =
            when (kind) {
                PeriodKind.MONTH -> DateSpan(month.firstDay, month.lastDay)
                PeriodKind.YEAR -> DateSpan(LocalDate(year, 1, 1), LocalDate(year, 12, 31))
            }

    fun previous(): PeriodCursor =
        when (kind) {
            PeriodKind.MONTH -> copy(month = month.minusMonth())
            PeriodKind.YEAR -> copy(month = YearMonth(year - 1, monthNumber))
        }

    fun next(): PeriodCursor =
        when (kind) {
            PeriodKind.MONTH -> copy(month = month.plusMonth())
            PeriodKind.YEAR -> copy(month = YearMonth(year + 1, monthNumber))
        }

    fun withKind(kind: PeriodKind): PeriodCursor = copy(kind = kind)

    fun drillDown(monthNumber: Int): PeriodCursor = PeriodCursor(PeriodKind.MONTH, YearMonth(year, monthNumber))

    companion object {
        fun monthOf(date: LocalDate): PeriodCursor = PeriodCursor(PeriodKind.MONTH, date.yearMonth)
    }
}
