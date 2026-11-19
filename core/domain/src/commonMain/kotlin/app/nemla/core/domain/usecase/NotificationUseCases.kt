package app.nemla.core.domain.usecase

import app.nemla.core.common.AppResult
import app.nemla.core.common.getOrNull
import app.nemla.core.common.map
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.ExpenseQuery
import app.nemla.core.domain.model.InboxChannel
import app.nemla.core.domain.model.InboxItem
import app.nemla.core.domain.model.InboxItemId
import app.nemla.core.domain.model.LocalNotice
import app.nemla.core.domain.model.NoticeGroup
import app.nemla.core.domain.model.NotificationAccess
import app.nemla.core.domain.model.NotificationPlanner
import app.nemla.core.domain.model.ReachedBudget
import app.nemla.core.domain.port.ExpenseRepository
import app.nemla.core.domain.port.InboxRepository
import app.nemla.core.domain.port.LocalNotifier
import app.nemla.core.domain.port.NotificationMemory
import app.nemla.core.domain.port.ProfileRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class RefreshScheduledNotifications(
    private val notifier: LocalNotifier,
    private val profiles: ProfileRepository,
    private val expenses: ExpenseRepository,
    private val languages: CurrentLanguages,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    suspend operator fun invoke(): AppResult<Unit> {
        val language = languages.ui()
        if (notifier.access() != NotificationAccess.GRANTED) {
            return firstFailure(
                notifier.schedule(NoticeGroup.REPORT_ARRIVAL, emptyList(), language),
                notifier.schedule(NoticeGroup.DAILY_REMINDER, emptyList(), language),
            )
        }
        val now = clock.now().toLocalDateTime(timeZone())
        val reports = notifier.schedule(NoticeGroup.REPORT_ARRIVAL, NotificationPlanner.reportArrivals(now), language)
        val profile = profiles.getProfile().getOrNull() ?: return reports
        val reminders =
            if (profile.dailyReminder) {
                val confirmedToday =
                    expenses
                        .list(ExpenseQuery(dateEquals = now.date, confirmed = true, limit = 1))
                        .getOrNull()
                        ?.isNotEmpty() ?: false
                NotificationPlanner.reminders(now, confirmedToday)
            } else {
                emptyList()
            }
        return firstFailure(reports, notifier.schedule(NoticeGroup.DAILY_REMINDER, reminders, language))
    }
}

class GetNotificationAccess(
    private val notifier: LocalNotifier,
) {
    suspend operator fun invoke(): NotificationAccess = notifier.access()
}

class SetDailyReminder(
    private val profiles: ProfileRepository,
    private val refresh: RefreshScheduledNotifications,
) {
    suspend operator fun invoke(enabled: Boolean): AppResult<Unit> {
        val saved = profiles.setDailyReminder(enabled)
        if (saved is AppResult.Failure) return saved
        refresh()
        return saved
    }
}

class AlertReachedBudgets(
    private val notifier: LocalNotifier,
    private val memory: NotificationMemory,
    private val languages: CurrentLanguages,
) {
    suspend operator fun invoke(
        month: YearMonth,
        budgets: List<ReachedBudget>,
        currency: Currency,
    ): Int {
        val reached = budgets.filter { it.isReached }
        if (reached.isEmpty() || notifier.access() != NotificationAccess.GRANTED) return 0
        val language = languages.ui()
        var shown = 0
        for (budget in reached) {
            val key = NotificationPlanner.budgetKey(month, budget.categoryId)
            if (memory.wasAlerted(key).getOrNull() != false) continue
            val notice = LocalNotice.BudgetReached(budget.categoryName, budget.usedCents, budget.allocatedCents, currency)
            if (notifier.show(notice, key, language) is AppResult.Success) {
                memory.remember(key)
                shown++
            }
        }
        return shown
    }
}

class DeliverPushInbox(
    private val inbox: InboxRepository,
    private val notifier: LocalNotifier,
    private val languages: CurrentLanguages,
) {
    suspend operator fun invoke(): AppResult<Int> {
        if (notifier.access() != NotificationAccess.GRANTED) return AppResult.Success(0)
        return inbox.unseen().map { items ->
            val language = languages.ui()
            items
                .filter { it.channel == InboxChannel.PUSH && !it.delivered }
                .count { item ->
                    notifier.show(LocalNotice.ServerMessage(item.title, item.body), item.id.value, language) is AppResult.Success &&
                        inbox.markDelivered(item.id) is AppResult.Success
                }
        }
    }
}

class ListInAppNotices(
    private val inbox: InboxRepository,
) {
    suspend operator fun invoke(): AppResult<List<InboxItem>> =
        inbox.unseen().map { items ->
            items.filter {
                it.channel ==
                    InboxChannel.IN_APP
            }
        }
}

class DismissInboxItem(
    private val inbox: InboxRepository,
) {
    suspend operator fun invoke(id: InboxItemId): AppResult<Unit> = inbox.markSeen(id)
}

class ClearNotifications(
    private val notifier: LocalNotifier,
    private val memory: NotificationMemory,
) {
    suspend operator fun invoke(): AppResult<Unit> = firstFailure(notifier.cancelAll(), memory.clear())
}

private fun firstFailure(vararg results: AppResult<Unit>): AppResult<Unit> =
    results.firstOrNull { it is AppResult.Failure } ?: AppResult.Success(Unit)
