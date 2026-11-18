package app.nemla.core.domain

import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.LocalNotice
import app.nemla.core.domain.model.NotificationPlanner
import app.nemla.core.domain.model.ReportCalendar
import app.nemla.core.domain.model.ReviewPeriod
import app.nemla.core.domain.model.ScheduledNotice
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPlannerTest {
    @Test
    fun remindersStartTodayAtEightPmForFourteenDays() {
        val plan = NotificationPlanner.reminders(LocalDateTime(2026, 11, 19, 9, 30), confirmedToday = false)
        assertEquals(14, plan.size)
        assertTrue(plan.all { it.notice == LocalNotice.DailyReminder })
        assertEquals(LocalDateTime(2026, 11, 19, 20, 0), plan.first().at)
        assertEquals(LocalDateTime(2026, 12, 2, 20, 0), plan.last().at)
    }

    @Test
    fun todayIsSkippedOnceAnExpenseIsConfirmed() {
        val plan = NotificationPlanner.reminders(LocalDateTime(2026, 11, 19, 9, 30), confirmedToday = true)
        assertEquals(13, plan.size)
        assertEquals(LocalDateTime(2026, 11, 20, 20, 0), plan.first().at)
    }

    @Test
    fun todayIsSkippedFromEightPm() {
        assertEquals(13, NotificationPlanner.reminders(LocalDateTime(2026, 11, 19, 20, 0), confirmedToday = false).size)
        assertEquals(14, NotificationPlanner.reminders(LocalDateTime(2026, 11, 19, 19, 59), confirmedToday = false).size)
        val lateDecember = NotificationPlanner.reminders(LocalDateTime(2026, 12, 31, 22, 0), confirmedToday = false)
        assertEquals(LocalDateTime(2027, 1, 1, 20, 0), lateDecember.first().at)
    }

    @Test
    fun reportArrivalsMatchTheBannerFirstDayForThreeMonths() {
        val plan = NotificationPlanner.reportArrivals(LocalDateTime(2026, 9, 1, 8, 0))
        assertEquals(
            listOf(
                ScheduledNotice(LocalNotice.ReportArrived(ReviewPeriod.Month(YearMonth(2026, 9))), LocalDateTime(2026, 9, 28, 10, 0)),
                ScheduledNotice(LocalNotice.ReportArrived(ReviewPeriod.Month(YearMonth(2026, 10))), LocalDateTime(2026, 10, 29, 10, 0)),
                ScheduledNotice(LocalNotice.ReportArrived(ReviewPeriod.Month(YearMonth(2026, 11))), LocalDateTime(2026, 11, 28, 10, 0)),
            ),
            plan,
        )
    }

    @Test
    fun februaryArrivalFollowsTheMonthLength() {
        assertEquals(LocalDate(2026, 2, 26), NotificationPlanner.arrivalDay(YearMonth(2026, 2)))
        assertEquals(LocalDate(2028, 2, 27), NotificationPlanner.arrivalDay(YearMonth(2028, 2)))
        val plan = NotificationPlanner.reportArrivals(LocalDateTime(2028, 1, 31, 12, 0))
        assertEquals(LocalDateTime(2028, 2, 27, 10, 0), plan.first().at)
    }

    @Test
    fun arrivalDayIsTheFirstBannerDay() {
        val months = listOf(YearMonth(2026, 2), YearMonth(2028, 2), YearMonth(2026, 4), YearMonth(2026, 12))
        for (month in months) {
            val day = NotificationPlanner.arrivalDay(month)
            assertTrue(ReportCalendar.arrivedPeriods(day).isNotEmpty())
            assertTrue(ReportCalendar.arrivedPeriods(LocalDate(day.year, day.month, day.day - 1)).isEmpty())
        }
    }

    @Test
    fun decemberAddsTheYearReport() {
        val plan = NotificationPlanner.reportArrivals(LocalDateTime(2026, 11, 29, 8, 0))
        assertEquals(
            listOf(
                ScheduledNotice(LocalNotice.ReportArrived(ReviewPeriod.Month(YearMonth(2026, 12))), LocalDateTime(2026, 12, 29, 10, 0)),
                ScheduledNotice(LocalNotice.ReportArrived(ReviewPeriod.Year(2026)), LocalDateTime(2026, 12, 29, 10, 0)),
                ScheduledNotice(LocalNotice.ReportArrived(ReviewPeriod.Month(YearMonth(2027, 1))), LocalDateTime(2027, 1, 29, 10, 0)),
            ),
            plan,
        )
    }

    @Test
    fun arrivalsMustBeStrictlyAfterNow() {
        val atArrival = NotificationPlanner.reportArrivals(LocalDateTime(2026, 11, 28, 10, 0))
        assertEquals(3, atArrival.size)
        assertFalse(atArrival.any { it.notice == LocalNotice.ReportArrived(ReviewPeriod.Month(YearMonth(2026, 11))) })
        val before = NotificationPlanner.reportArrivals(LocalDateTime(2026, 11, 28, 9, 59))
        assertEquals(LocalDateTime(2026, 11, 28, 10, 0), before.first().at)
    }

    @Test
    fun budgetKeysCombineMonthAndCategory() {
        assertEquals("2026-03:cat-1", NotificationPlanner.budgetKey(YearMonth(2026, 3), CategoryId("cat-1")))
    }

    @Test
    fun sensitiveNoticesAreMasked() {
        val budget = LocalNotice.BudgetReached("Courses", 50_000, 40_000, Currency.EUR)
        assertFalse(budget.toString().contains("Courses"))
        assertFalse(budget.toString().contains("50000"))
        assertFalse(LocalNotice.ServerMessage("Titre secret", "Corps secret").toString().contains("secret"))
        assertEquals(budget, LocalNotice.BudgetReached("Courses", 50_000, 40_000, Currency.EUR))
    }
}
