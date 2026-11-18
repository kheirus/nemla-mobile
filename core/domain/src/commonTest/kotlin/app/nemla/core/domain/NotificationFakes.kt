package app.nemla.core.domain

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.AppPreferences
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.Expense
import app.nemla.core.domain.model.ExpenseEdit
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ExpenseQuery
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.InboxItem
import app.nemla.core.domain.model.InboxItemId
import app.nemla.core.domain.model.LocalNotice
import app.nemla.core.domain.model.NoticeGroup
import app.nemla.core.domain.model.NotificationAccess
import app.nemla.core.domain.model.ProfileIdentity
import app.nemla.core.domain.model.PseudoAvailability
import app.nemla.core.domain.model.ScheduledNotice
import app.nemla.core.domain.model.UserId
import app.nemla.core.domain.model.UserProfile
import app.nemla.core.domain.port.DeviceLanguage
import app.nemla.core.domain.port.ExpenseRepository
import app.nemla.core.domain.port.InboxRepository
import app.nemla.core.domain.port.LocalNotifier
import app.nemla.core.domain.port.NotificationMemory
import app.nemla.core.domain.port.PreferencesRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.RefreshScheduledNotifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant

internal class FakeNotifier(
    var access: NotificationAccess = NotificationAccess.GRANTED,
) : LocalNotifier {
    val scheduled = mutableMapOf<NoticeGroup, List<ScheduledNotice>>()
    val scheduleCalls = mutableListOf<NoticeGroup>()
    val shown = mutableListOf<Triple<LocalNotice, String, AppLanguage>>()
    val languages = mutableListOf<AppLanguage>()
    var showResult: AppResult<Unit> = AppResult.Success(Unit)
    var cancelled = 0

    override suspend fun access(): NotificationAccess = access

    override suspend fun schedule(
        group: NoticeGroup,
        notices: List<ScheduledNotice>,
        language: AppLanguage,
    ): AppResult<Unit> {
        scheduleCalls += group
        scheduled[group] = notices
        languages += language
        return AppResult.Success(Unit)
    }

    override suspend fun show(
        notice: LocalNotice,
        tag: String,
        language: AppLanguage,
    ): AppResult<Unit> {
        if (showResult is AppResult.Success) shown += Triple(notice, tag, language)
        return showResult
    }

    override suspend fun cancelAll(): AppResult<Unit> {
        cancelled++
        return AppResult.Success(Unit)
    }
}

internal class FakeMemory : NotificationMemory {
    val keys = mutableSetOf<String>()
    var readFails = false
    var cleared = 0

    override suspend fun wasAlerted(key: String): AppResult<Boolean> =
        if (readFails) AppResult.Failure(AppError.Storage) else AppResult.Success(key in keys)

    override suspend fun remember(key: String): AppResult<Unit> {
        keys += key
        return AppResult.Success(Unit)
    }

    override suspend fun clear(): AppResult<Unit> {
        cleared++
        keys.clear()
        return AppResult.Success(Unit)
    }
}

internal class FakeInbox(
    var items: AppResult<List<InboxItem>> = AppResult.Success(emptyList()),
) : InboxRepository {
    val seen = mutableListOf<InboxItemId>()
    val delivered = mutableListOf<InboxItemId>()

    override suspend fun unseen(): AppResult<List<InboxItem>> = items

    override suspend fun markSeen(id: InboxItemId): AppResult<Unit> {
        seen += id
        return AppResult.Success(Unit)
    }

    override suspend fun markDelivered(id: InboxItemId): AppResult<Unit> {
        delivered += id
        return AppResult.Success(Unit)
    }
}

internal class ReminderProfiles(
    var result: AppResult<UserProfile> = AppResult.Success(reminderProfile(true)),
) : ProfileRepository {
    val reminderUpdates = mutableListOf<Boolean>()
    var setResult: AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun bootstrap(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun getProfile(): AppResult<UserProfile> = result

    override suspend fun updateIdentity(identity: ProfileIdentity): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun updateBudget(budget: GlobalBudget): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun setDailyReminder(enabled: Boolean): AppResult<Unit> {
        reminderUpdates += enabled
        if (setResult is AppResult.Success) result = AppResult.Success(reminderProfile(enabled))
        return setResult
    }

    override suspend fun updateCurrency(currency: Currency): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun completeOnboarding(monthlyBudgetCents: Long): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun checkPseudo(pseudo: String): AppResult<PseudoAvailability> =
        AppResult.Success(PseudoAvailability(true, emptyList()))
}

internal fun reminderProfile(enabled: Boolean): UserProfile =
    UserProfile(
        userId = UserId("user-1"),
        pseudo = null,
        age = null,
        gender = null,
        household = null,
        currency = Currency.EUR,
        budget = GlobalBudget(0, null, BudgetPeriod.MONTH),
        onboardingDone = true,
        dailyReminder = enabled,
    )

internal class TodayExpenses(
    var result: AppResult<List<Expense>> = AppResult.Success(emptyList()),
) : ExpenseRepository {
    val queries = mutableListOf<ExpenseQuery>()

    override suspend fun list(query: ExpenseQuery): AppResult<List<Expense>> {
        queries += query
        return result
    }

    override suspend fun searchByLabel(
        term: String,
        limit: Int,
    ): AppResult<List<Expense>> = AppResult.Success(emptyList())

    override suspend fun update(
        id: ExpenseId,
        edit: ExpenseEdit,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun delete(id: ExpenseId): AppResult<Unit> = AppResult.Success(Unit)
}

internal class FixedLanguagePreferences(
    language: AppLanguage?,
) : PreferencesRepository {
    private val state = MutableStateFlow<AppPreferences?>(AppPreferences(appLanguage = language))
    override val preferences: StateFlow<AppPreferences?> = state

    override suspend fun load(): AppPreferences = state.value ?: AppPreferences.Default

    override suspend fun update(preferences: AppPreferences): AppResult<Unit> {
        state.value = preferences
        return AppResult.Success(Unit)
    }
}

internal fun languages(language: AppLanguage? = AppLanguage.FR): CurrentLanguages =
    CurrentLanguages(FixedLanguagePreferences(language), DeviceLanguage { AppLanguage.FR })

internal class FixedInstantClock(
    var instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}

internal fun clockAt(local: LocalDateTime): FixedInstantClock = FixedInstantClock(local.toInstant(TimeZone.UTC))

internal fun refreshWith(
    notifier: FakeNotifier = FakeNotifier(),
    profiles: ReminderProfiles = ReminderProfiles(),
    expenses: TodayExpenses = TodayExpenses(),
    language: AppLanguage? = AppLanguage.FR,
    now: LocalDateTime = LocalDateTime(2026, 11, 19, 9, 0),
): RefreshScheduledNotifications =
    RefreshScheduledNotifications(notifier, profiles, expenses, languages(language), clockAt(now)) { TimeZone.UTC }
