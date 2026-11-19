package app.nemla.feature.voice

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.Expense
import app.nemla.core.domain.model.ExpenseEdit
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ExpenseQuery
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.LocalNotice
import app.nemla.core.domain.model.NoticeGroup
import app.nemla.core.domain.model.NotificationAccess
import app.nemla.core.domain.model.ProfileIdentity
import app.nemla.core.domain.model.PseudoAvailability
import app.nemla.core.domain.model.ScheduledNotice
import app.nemla.core.domain.model.UserProfile
import app.nemla.core.domain.port.DeviceLanguage
import app.nemla.core.domain.port.ExpenseRepository
import app.nemla.core.domain.port.LocalNotifier
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.RefreshScheduledNotifications

internal class FakeNotifier : LocalNotifier {
    val scheduled = mutableListOf<NoticeGroup>()

    override suspend fun access(): NotificationAccess = NotificationAccess.DENIED

    override suspend fun schedule(
        group: NoticeGroup,
        notices: List<ScheduledNotice>,
        language: AppLanguage,
    ): AppResult<Unit> {
        scheduled += group
        return AppResult.Success(Unit)
    }

    override suspend fun show(
        notice: LocalNotice,
        tag: String,
        language: AppLanguage,
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun cancelAll(): AppResult<Unit> = AppResult.Success(Unit)
}

internal object UnreadableProfiles : ProfileRepository {
    override suspend fun bootstrap(): AppResult<Unit> = AppResult.Failure(AppError.Unknown)

    override suspend fun getProfile(): AppResult<UserProfile> = AppResult.Failure(AppError.Unknown)

    override suspend fun updateIdentity(identity: ProfileIdentity): AppResult<Unit> = AppResult.Failure(AppError.Unknown)

    override suspend fun updateBudget(budget: GlobalBudget): AppResult<Unit> = AppResult.Failure(AppError.Unknown)

    override suspend fun setDailyReminder(enabled: Boolean): AppResult<Unit> = AppResult.Failure(AppError.Unknown)

    override suspend fun updateCurrency(currency: Currency): AppResult<Unit> = AppResult.Failure(AppError.Unknown)

    override suspend fun completeOnboarding(monthlyBudgetCents: Long): AppResult<Unit> = AppResult.Failure(AppError.Unknown)

    override suspend fun checkPseudo(pseudo: String): AppResult<PseudoAvailability> = AppResult.Failure(AppError.Unknown)
}

internal object NoExpenses : ExpenseRepository {
    override suspend fun list(query: ExpenseQuery): AppResult<List<Expense>> = AppResult.Success(emptyList())

    override suspend fun searchByLabel(
        term: String,
        limit: Int,
    ): AppResult<List<Expense>> = AppResult.Success(emptyList())

    override suspend fun update(
        id: ExpenseId,
        edit: ExpenseEdit,
    ): AppResult<Unit> = AppResult.Failure(AppError.Unknown)

    override suspend fun delete(id: ExpenseId): AppResult<Unit> = AppResult.Failure(AppError.Unknown)
}

internal fun testRefresh(notifier: LocalNotifier = FakeNotifier()): RefreshScheduledNotifications =
    RefreshScheduledNotifications(
        notifier = notifier,
        profiles = UnreadableProfiles,
        expenses = NoExpenses,
        languages = CurrentLanguages(FakePreferences(), DeviceLanguage { AppLanguage.FR }),
    )
