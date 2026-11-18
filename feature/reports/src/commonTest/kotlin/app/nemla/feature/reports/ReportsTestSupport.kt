package app.nemla.feature.reports

import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.AppPreferences
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.CategoryShare
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.DateSpan
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.InboxChannel
import app.nemla.core.domain.model.InboxItem
import app.nemla.core.domain.model.InboxItemId
import app.nemla.core.domain.model.InboxKind
import app.nemla.core.domain.model.PeriodReview
import app.nemla.core.domain.model.ProfileIdentity
import app.nemla.core.domain.model.PseudoAvailability
import app.nemla.core.domain.model.ReportSheet
import app.nemla.core.domain.model.ReviewPeriod
import app.nemla.core.domain.model.UserId
import app.nemla.core.domain.model.UserProfile
import app.nemla.core.domain.port.InboxRepository
import app.nemla.core.domain.port.PeriodReviewer
import app.nemla.core.domain.port.PreferencesRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.ReportRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest {
    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }
}

internal fun month(
    year: Int,
    number: Int,
): ReviewPeriod = ReviewPeriod.Month(YearMonth(year, number))

internal fun sampleReview(period: ReviewPeriod): PeriodReview =
    PeriodReview(
        period,
        period,
        123_450,
        100_000,
        150_000,
        42,
        listOf(CategoryShare("Courses", 40_025, 32, -1_250)),
        listOf("h"),
        listOf("a"),
    )

internal class FakeReviewer : PeriodReviewer {
    val results = ArrayDeque<AppResult<PeriodReview>>()
    val calls = mutableListOf<ReviewPeriod>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun review(period: ReviewPeriod): AppResult<PeriodReview> {
        calls += period
        gate?.await()
        val next: AppResult<PeriodReview> = results.removeFirstOrNull() ?: AppResult.Success(sampleReview(period))
        return next
    }
}

internal class FakeReports : ReportRepository {
    var viewed: AppResult<Set<ReviewPeriod>> = AppResult.Success(emptySet())
    var months: AppResult<Set<YearMonth>> = AppResult.Success(emptySet())
    val marked = mutableListOf<ReviewPeriod>()
    val spans = mutableListOf<DateSpan>()

    override suspend fun viewedPeriods(): AppResult<Set<ReviewPeriod>> = viewed

    override suspend fun markViewed(period: ReviewPeriod): AppResult<Unit> {
        marked += period
        return AppResult.Success(Unit)
    }

    override suspend fun monthsWithConfirmedExpenses(span: DateSpan): AppResult<Set<YearMonth>> {
        spans += span
        return months
    }
}

internal class FakeProfiles(
    private val currency: Currency = Currency.CHF,
) : ProfileRepository {
    override suspend fun bootstrap(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun getProfile(): AppResult<UserProfile> =
        AppResult.Success(
            UserProfile(UserId("u-1"), null, null, null, null, currency, GlobalBudget(0, null, BudgetPeriod.MONTH), true, false),
        )

    override suspend fun updateIdentity(identity: ProfileIdentity): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun updateBudget(budget: GlobalBudget): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun setDailyReminder(enabled: Boolean): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun updateCurrency(currency: Currency): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun completeOnboarding(monthlyBudgetCents: Long): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun checkPseudo(pseudo: String): AppResult<PseudoAvailability> =
        AppResult.Success(PseudoAvailability(true, emptyList()))
}

internal class FakePreferences : PreferencesRepository {
    private val state = MutableStateFlow<AppPreferences?>(AppPreferences(appLanguage = AppLanguage.EN))
    override val preferences: StateFlow<AppPreferences?> = state

    override suspend fun load(): AppPreferences = state.value ?: AppPreferences.Default

    override suspend fun update(preferences: AppPreferences): AppResult<Unit> {
        state.value = preferences
        return AppResult.Success(Unit)
    }
}

internal fun inboxItem(
    id: String,
    kind: InboxKind = InboxKind.MONTH_REPORT,
    channel: InboxChannel = InboxChannel.IN_APP,
    period: ReviewPeriod? = null,
    report: ReportSheet? = null,
) = InboxItem(InboxItemId(id), channel, kind, "Titre $id", "Corps $id", period, report, false)

internal class FakeInbox : InboxRepository {
    var items: AppResult<List<InboxItem>> = AppResult.Success(emptyList())
    val seen = mutableListOf<InboxItemId>()

    override suspend fun unseen(): AppResult<List<InboxItem>> = items

    override suspend fun markSeen(id: InboxItemId): AppResult<Unit> {
        seen += id
        return AppResult.Success(Unit)
    }

    override suspend fun markDelivered(id: InboxItemId): AppResult<Unit> = AppResult.Success(Unit)
}
