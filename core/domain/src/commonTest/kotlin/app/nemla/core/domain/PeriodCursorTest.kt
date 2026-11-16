package app.nemla.core.domain

import app.nemla.core.domain.model.DateSpan
import app.nemla.core.domain.model.PeriodCursor
import app.nemla.core.domain.model.PeriodKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeriodCursorTest {
    private val november = PeriodCursor.monthOf(LocalDate(2026, 11, 16))

    @Test
    fun monthSpanCoversTheWholeMonth() {
        assertEquals(DateSpan(LocalDate(2026, 11, 1), LocalDate(2026, 11, 30)), november.span)
        assertEquals(LocalDate(2028, 2, 29), PeriodCursor(PeriodKind.MONTH, YearMonth(2028, 2)).span.to)
    }

    @Test
    fun yearSpanCoversTheWholeYear() {
        assertEquals(DateSpan(LocalDate(2026, 1, 1), LocalDate(2026, 12, 31)), november.withKind(PeriodKind.YEAR).span)
    }

    @Test
    fun navigationIsUnboundedInBothDirections() {
        assertEquals(YearMonth(2027, 1), november.next().next().month)
        assertEquals(YearMonth(2025, 12), generateSequence(november) { it.previous() }.elementAt(11).month)
        val year = november.withKind(PeriodKind.YEAR)
        assertEquals(
            2030,
            year
                .next()
                .next()
                .next()
                .next()
                .year,
        )
        assertEquals(1999, generateSequence(year) { it.previous() }.elementAt(27).year)
    }

    @Test
    fun yearModeKeepsTheMonthForTheWayBack() {
        val back = november.withKind(PeriodKind.YEAR).next().withKind(PeriodKind.MONTH)
        assertEquals(YearMonth(2027, 11), back.month)
    }

    @Test
    fun drillDownOpensTheMonthOfTheSameYear() {
        assertEquals(PeriodCursor(PeriodKind.MONTH, YearMonth(2026, 3)), november.withKind(PeriodKind.YEAR).drillDown(3))
    }

    @Test
    fun spanContainsItsBounds() {
        assertTrue(LocalDate(2026, 11, 30) in november.span)
        assertFalse(LocalDate(2026, 12, 1) in november.span)
    }
}
