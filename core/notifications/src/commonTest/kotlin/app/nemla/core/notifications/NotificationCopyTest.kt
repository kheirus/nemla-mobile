package app.nemla.core.notifications

import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.LocalNotice
import app.nemla.core.domain.model.ReviewPeriod
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationCopyTest {
    @Test
    fun dailyReminderFollowsTheWebText() {
        val french = NotificationCopy.text(LocalNotice.DailyReminder, AppLanguage.FR)
        assertEquals("nemla", french.title)
        assertEquals("Bilan de la journée : dictez vos dépenses du jour en 30 secondes.", french.body)
        assertEquals(
            "Daily check-in: dictate today's expenses in 30 seconds.",
            NotificationCopy.text(LocalNotice.DailyReminder, AppLanguage.EN).body,
        )
    }

    @Test
    fun reportArrivalNamesThePeriod() {
        val month = LocalNotice.ReportArrived(ReviewPeriod.Month(YearMonth(2026, 8)))
        assertEquals("Votre bilan de Août 2026 est arrivé", NotificationCopy.text(month, AppLanguage.FR).body)
        assertEquals("Your report for August 2026 is ready", NotificationCopy.text(month, AppLanguage.EN).body)
        val year = LocalNotice.ReportArrived(ReviewPeriod.Year(2026))
        assertEquals("Votre bilan de l'année 2026 est arrivé", NotificationCopy.text(year, AppLanguage.FR).body)
        assertEquals("Your report for the year 2026 is ready", NotificationCopy.text(year, AppLanguage.EN).body)
        assertEquals("nemla", NotificationCopy.text(year, AppLanguage.EN).title)
    }

    @Test
    fun budgetReachedFormatsMoney() {
        val notice = LocalNotice.BudgetReached("Courses", 41_050, 40_000, Currency.EUR)
        val french = NotificationCopy.text(notice, AppLanguage.FR)
        assertEquals("Budget atteint", french.title)
        assertEquals("Courses : 410,50\u00A0€ dépensés sur 400,00\u00A0€ prévus ce mois-ci.", french.body)
        val english = NotificationCopy.text(LocalNotice.BudgetReached("Food", 5_000, 5_000, Currency.USD), AppLanguage.EN)
        assertEquals("Budget reached", english.title)
        assertEquals("Food: \$50.00 spent of \$50.00 planned this month.", english.body)
    }

    @Test
    fun serverMessagesKeepTheirTextAndArePrivate() {
        val message = LocalNotice.ServerMessage("", "Corps")
        assertEquals("nemla", NotificationCopy.text(message, AppLanguage.FR).title)
        assertEquals("Corps", NotificationCopy.text(message, AppLanguage.FR).body)
        assertTrue(NotificationCopy.isPrivate(message))
        assertTrue(NotificationCopy.isPrivate(LocalNotice.BudgetReached("A", 1, 1, Currency.EUR)))
        assertFalse(NotificationCopy.isPrivate(LocalNotice.DailyReminder))
        assertFalse(NotificationCopy.text(message, AppLanguage.FR).toString().contains("Corps"))
    }

    @Test
    fun everyNoticeHasAChannelWithTranslatedNames() {
        assertEquals(NoticeChannel.REMINDERS, NoticeChannel.of(LocalNotice.DailyReminder))
        assertEquals(NoticeChannel.REPORTS, NoticeChannel.of(LocalNotice.ReportArrived(ReviewPeriod.Year(2026))))
        assertEquals(NoticeChannel.BUDGETS, NoticeChannel.of(LocalNotice.BudgetReached("A", 1, 1, Currency.EUR)))
        assertEquals(NoticeChannel.MESSAGES, NoticeChannel.of(LocalNotice.ServerMessage("a", "b")))
        assertEquals(NoticeChannel.REPORTS, NoticeChannel.fromId("reports"))
        assertEquals(NoticeChannel.MESSAGES, NoticeChannel.fromId("unknown"))
        NoticeChannel.entries.forEach { channel ->
            assertTrue(NotificationCopy.channelName(channel, AppLanguage.FR).isNotBlank())
            assertTrue(NotificationCopy.channelName(channel, AppLanguage.EN) != NotificationCopy.channelName(channel, AppLanguage.FR))
        }
    }
}
