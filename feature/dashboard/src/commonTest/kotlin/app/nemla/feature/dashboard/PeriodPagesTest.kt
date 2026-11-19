package app.nemla.feature.dashboard

import app.nemla.core.domain.model.PeriodCursor
import app.nemla.core.domain.model.PeriodKind
import app.nemla.feature.dashboard.logic.PeriodPages
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeriodPagesTest {
    private val today = LocalDate(2026, 11, 16)
    private val month = PeriodCursor.monthOf(today)
    private val year = month.withKind(PeriodKind.YEAR)

    @Test
    fun todaysPeriodIsTheLastPage() {
        assertEquals(PeriodPages.count(PeriodKind.MONTH, today) - 1, PeriodPages.pageOf(month))
        assertEquals(PeriodPages.count(PeriodKind.YEAR, today) - 1, PeriodPages.pageOf(year))
        assertFalse(PeriodPages.hasNext(month, today))
        assertFalse(PeriodPages.hasNext(year, today))
    }

    @Test
    fun firstPageIsJanuaryOfTheEarliestYear() {
        val first = PeriodPages.cursorAt(0, month, today)
        assertEquals(YearMonth(PeriodPages.EARLIEST_YEAR, 1), first.month)
        assertEquals(0, PeriodPages.pageOf(first))
        assertFalse(PeriodPages.hasPrevious(first))
        assertTrue(PeriodPages.hasPrevious(month))
    }

    @Test
    fun pagesAndCursorsRoundTrip() {
        val march = PeriodCursor(PeriodKind.MONTH, YearMonth(2026, 3))
        assertEquals(march, PeriodPages.cursorAt(PeriodPages.pageOf(march), month, today))
        val december = PeriodCursor(PeriodKind.MONTH, YearMonth(2025, 12))
        assertEquals(december, PeriodPages.cursorAt(PeriodPages.pageOf(december), month, today))
        val past = PeriodCursor(PeriodKind.YEAR, YearMonth(2019, 11))
        assertEquals(past, PeriodPages.cursorAt(PeriodPages.pageOf(past), year, today))
    }

    @Test
    fun pagesOutsideTheRangeAreBounded() {
        assertEquals(month, PeriodPages.cursorAt(PeriodPages.pageOf(month) + 3, month, today))
        assertEquals(YearMonth(PeriodPages.EARLIEST_YEAR, 1), PeriodPages.cursorAt(-4, month, today).month)
    }

    @Test
    fun clampKeepsYearModeOffFutureMonths() {
        val carried = PeriodCursor(PeriodKind.YEAR, YearMonth(2026, 12))
        assertEquals(PeriodCursor(PeriodKind.YEAR, YearMonth(2026, 11)), PeriodPages.clamp(carried, today))
        assertFalse(PeriodPages.isWithinBounds(carried, today))
        assertTrue(PeriodPages.isWithinBounds(year, today))
    }
}
