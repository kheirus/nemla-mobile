package app.nemla.core.domain

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.Expense
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ExpenseQuery
import app.nemla.core.domain.model.ExpenseSource
import app.nemla.core.domain.model.InboxChannel
import app.nemla.core.domain.model.InboxItem
import app.nemla.core.domain.model.InboxItemId
import app.nemla.core.domain.model.InboxKind
import app.nemla.core.domain.model.LocalNotice
import app.nemla.core.domain.model.NoticeGroup
import app.nemla.core.domain.model.NotificationAccess
import app.nemla.core.domain.model.PaymentMethod
import app.nemla.core.domain.model.ReachedBudget
import app.nemla.core.domain.usecase.AlertReachedBudgets
import app.nemla.core.domain.usecase.ClearNotifications
import app.nemla.core.domain.usecase.DeliverPushInbox
import app.nemla.core.domain.usecase.DismissInboxItem
import app.nemla.core.domain.usecase.ListInAppNotices
import app.nemla.core.domain.usecase.SetDailyReminder
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationUseCasesTest {
    private val notifier = FakeNotifier()
    private val profiles = ReminderProfiles()
    private val expenses = TodayExpenses()
    private val memory = FakeMemory()
    private val inbox = FakeInbox()
    private val november = YearMonth(2026, 11)

    @Test
    fun refreshSchedulesBothGroupsInTheInterfaceLanguage() =
        runTest {
            val result = refreshWith(notifier, profiles, expenses, AppLanguage.EN)()
            assertEquals(AppResult.Success(Unit), result)
            assertEquals(14, notifier.scheduled.getValue(NoticeGroup.DAILY_REMINDER).size)
            assertEquals(4, notifier.scheduled.getValue(NoticeGroup.REPORT_ARRIVAL).size)
            assertTrue(notifier.languages.all { it == AppLanguage.EN })
            assertEquals(ExpenseQuery(dateEquals = LocalDate(2026, 11, 19), confirmed = true, limit = 1), expenses.queries.single())
        }

    @Test
    fun refreshSkipsTodayWhenAnExpenseIsConfirmed() =
        runTest {
            expenses.result = AppResult.Success(listOf(sampleExpense()))
            refreshWith(notifier, profiles, expenses)()
            val reminders = notifier.scheduled.getValue(NoticeGroup.DAILY_REMINDER)
            assertEquals(13, reminders.size)
            assertEquals(LocalDateTime(2026, 11, 20, 20, 0), reminders.first().at)
        }

    @Test
    fun refreshEmptiesBothGroupsWithoutPermission() =
        runTest {
            notifier.access = NotificationAccess.DENIED
            refreshWith(notifier, profiles, expenses)()
            assertEquals(emptyList(), notifier.scheduled.getValue(NoticeGroup.DAILY_REMINDER))
            assertEquals(emptyList(), notifier.scheduled.getValue(NoticeGroup.REPORT_ARRIVAL))
            assertTrue(expenses.queries.isEmpty())
        }

    @Test
    fun refreshEmptiesTheReminderWhenItIsOff() =
        runTest {
            profiles.result = AppResult.Success(reminderProfile(false))
            refreshWith(notifier, profiles, expenses)()
            assertEquals(emptyList(), notifier.scheduled.getValue(NoticeGroup.DAILY_REMINDER))
            assertEquals(4, notifier.scheduled.getValue(NoticeGroup.REPORT_ARRIVAL).size)
        }

    @Test
    fun unreadableProfileLeavesTheReminderGroupUntouched() =
        runTest {
            profiles.result = AppResult.Failure(AppError.Network.Offline)
            refreshWith(notifier, profiles, expenses)()
            assertEquals(listOf(NoticeGroup.REPORT_ARRIVAL), notifier.scheduleCalls)
        }

    @Test
    fun settingTheReminderSavesThenReschedules() =
        runTest {
            val result = SetDailyReminder(profiles, refreshWith(notifier, profiles, expenses))(false)
            assertEquals(AppResult.Success(Unit), result)
            assertEquals(listOf(false), profiles.reminderUpdates)
            assertEquals(emptyList(), notifier.scheduled.getValue(NoticeGroup.DAILY_REMINDER))
        }

    @Test
    fun aFailedReminderSaveDoesNotReschedule() =
        runTest {
            profiles.setResult = AppResult.Failure(AppError.Network.Offline)
            val result = SetDailyReminder(profiles, refreshWith(notifier, profiles, expenses))(true)
            assertEquals(AppResult.Failure(AppError.Network.Offline), result)
            assertTrue(notifier.scheduleCalls.isEmpty())
        }

    @Test
    fun reachedBudgetsAreShownOncePerMonth() =
        runTest {
            val alert = AlertReachedBudgets(notifier, memory, languages(AppLanguage.EN))
            val budgets =
                listOf(
                    ReachedBudget(CategoryId("c-1"), "Courses", 40_000, 40_000),
                    ReachedBudget(CategoryId("c-2"), "Loisirs", 9_000, 10_000),
                    ReachedBudget(CategoryId("c-3"), "Divers", 500, 0),
                )
            assertEquals(1, alert(november, budgets, Currency.EUR))
            val (notice, tag, language) = notifier.shown.single()
            assertEquals(LocalNotice.BudgetReached("Courses", 40_000, 40_000, Currency.EUR), notice)
            assertEquals("2026-11:c-1", tag)
            assertEquals(AppLanguage.EN, language)
            assertEquals(setOf("2026-11:c-1"), memory.keys)
            assertEquals(0, alert(november, budgets, Currency.EUR))
            assertEquals(1, alert(YearMonth(2026, 12), budgets, Currency.EUR))
        }

    @Test
    fun budgetAlertsWaitForPermissionAndReadableMemory() =
        runTest {
            val budgets = listOf(ReachedBudget(CategoryId("c-1"), "Courses", 50_000, 40_000))
            notifier.access = NotificationAccess.UNDETERMINED
            assertEquals(0, AlertReachedBudgets(notifier, memory, languages())(november, budgets, Currency.EUR))
            assertTrue(memory.keys.isEmpty())
            notifier.access = NotificationAccess.GRANTED
            memory.readFails = true
            assertEquals(0, AlertReachedBudgets(notifier, memory, languages())(november, budgets, Currency.EUR))
            memory.readFails = false
            notifier.showResult = AppResult.Failure(AppError.Unknown)
            assertEquals(0, AlertReachedBudgets(notifier, memory, languages())(november, budgets, Currency.EUR))
            assertTrue(memory.keys.isEmpty())
        }

    @Test
    fun pushItemsAreShownThenMarkedDelivered() =
        runTest {
            inbox.items =
                AppResult.Success(
                    listOf(
                        item("p-1", InboxChannel.PUSH),
                        item("p-2", InboxChannel.PUSH, delivered = true),
                        item("i-1", InboxChannel.IN_APP),
                    ),
                )
            assertEquals(AppResult.Success(1), DeliverPushInbox(inbox, notifier, languages())())
            val (notice, tag) = notifier.shown.single()
            assertEquals(LocalNotice.ServerMessage("Titre p-1", "Corps p-1"), notice)
            assertEquals("p-1", tag)
            assertEquals(listOf(InboxItemId("p-1")), inbox.delivered)
        }

    @Test
    fun pushItemsStayPendingWithoutPermissionOrWhenShowingFails() =
        runTest {
            inbox.items = AppResult.Success(listOf(item("p-1", InboxChannel.PUSH)))
            notifier.access = NotificationAccess.DENIED
            assertEquals(AppResult.Success(0), DeliverPushInbox(inbox, notifier, languages())())
            notifier.access = NotificationAccess.GRANTED
            notifier.showResult = AppResult.Failure(AppError.Unknown)
            assertEquals(AppResult.Success(0), DeliverPushInbox(inbox, notifier, languages())())
            assertTrue(inbox.delivered.isEmpty())
            inbox.items = AppResult.Failure(AppError.Network.Offline)
            notifier.showResult = AppResult.Success(Unit)
            assertEquals(AppResult.Failure(AppError.Network.Offline), DeliverPushInbox(inbox, notifier, languages())())
        }

    @Test
    fun inAppNoticesAreFilteredAndDismissed() =
        runTest {
            inbox.items = AppResult.Success(listOf(item("p-1", InboxChannel.PUSH), item("i-1", InboxChannel.IN_APP)))
            assertEquals(listOf(InboxItemId("i-1")), (ListInAppNotices(inbox)() as AppResult.Success).value.map { it.id })
            assertEquals(AppResult.Success(Unit), DismissInboxItem(inbox)(InboxItemId("i-1")))
            assertEquals(listOf(InboxItemId("i-1")), inbox.seen)
        }

    @Test
    fun clearingCancelsEverythingAndForgetsAlerts() =
        runTest {
            memory.keys += "2026-11:c-1"
            assertEquals(AppResult.Success(Unit), ClearNotifications(notifier, memory)())
            assertEquals(1, notifier.cancelled)
            assertTrue(memory.keys.isEmpty())
        }

    private fun item(
        id: String,
        channel: InboxChannel,
        delivered: Boolean = false,
    ) = InboxItem(InboxItemId(id), channel, InboxKind.MONTH_REPORT, "Titre $id", "Corps $id", null, null, delivered)

    private fun sampleExpense() =
        Expense(
            ExpenseId("e-1"),
            null,
            null,
            null,
            1_000,
            LocalDate(2026, 11, 19),
            LocalDate(2026, 11, 19),
            ExpenseSource.VOICE,
            PaymentMethod.CARD,
            true,
            null,
        )
}
