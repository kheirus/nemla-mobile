package app.nemla.feature.dashboard.logic

import app.nemla.core.domain.model.PeriodCursor
import app.nemla.core.domain.model.PeriodKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.number
import kotlinx.datetime.yearMonth

internal object PeriodPages {
    const val EARLIEST_YEAR = 2000
    private const val MONTHS = 12

    fun count(
        kind: PeriodKind,
        today: LocalDate,
    ): Int =
        when (kind) {
            PeriodKind.MONTH -> (today.year - EARLIEST_YEAR) * MONTHS + today.month.number
            PeriodKind.YEAR -> today.year - EARLIEST_YEAR + 1
        }

    fun pageOf(cursor: PeriodCursor): Int =
        when (cursor.kind) {
            PeriodKind.MONTH -> (cursor.year - EARLIEST_YEAR) * MONTHS + cursor.monthNumber - 1
            PeriodKind.YEAR -> cursor.year - EARLIEST_YEAR
        }

    fun cursorAt(
        page: Int,
        current: PeriodCursor,
        today: LocalDate,
    ): PeriodCursor {
        val bounded = page.coerceIn(0, count(current.kind, today) - 1)
        val cursor =
            when (current.kind) {
                PeriodKind.MONTH -> PeriodCursor(PeriodKind.MONTH, YearMonth(EARLIEST_YEAR + bounded / MONTHS, bounded % MONTHS + 1))
                PeriodKind.YEAR -> PeriodCursor(PeriodKind.YEAR, YearMonth(EARLIEST_YEAR + bounded, current.monthNumber))
            }
        return clamp(cursor, today)
    }

    fun clamp(
        cursor: PeriodCursor,
        today: LocalDate,
    ): PeriodCursor {
        val earliest = YearMonth(EARLIEST_YEAR, 1)
        val latest = today.yearMonth
        return when {
            cursor.month > latest -> cursor.copy(month = latest)
            cursor.month < earliest -> cursor.copy(month = earliest)
            else -> cursor
        }
    }

    fun isWithinBounds(
        cursor: PeriodCursor,
        today: LocalDate,
    ): Boolean = clamp(cursor, today) == cursor

    fun hasPrevious(cursor: PeriodCursor): Boolean = pageOf(cursor) > 0

    fun hasNext(
        cursor: PeriodCursor,
        today: LocalDate,
    ): Boolean = pageOf(cursor) < count(cursor.kind, today) - 1
}
