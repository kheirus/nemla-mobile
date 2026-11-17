package app.nemla.core.domain

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.getOrNull
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.DateSpan
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.PeriodReview
import app.nemla.core.domain.model.ProfileIdentity
import app.nemla.core.domain.model.PseudoAvailability
import app.nemla.core.domain.model.ReportCalendar
import app.nemla.core.domain.model.ReportYearGroup
import app.nemla.core.domain.model.ReviewPeriod
import app.nemla.core.domain.model.UserId
import app.nemla.core.domain.model.UserProfile
import app.nemla.core.domain.port.PeriodReviewer
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.ReportRepository
import app.nemla.core.domain.usecase.FindArrivedReports
import app.nemla.core.domain.usecase.ListReportHistory
import app.nemla.core.domain.usecase.LoadPeriodReport
import app.nemla.core.domain.usecase.MarkReportViewed
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportUseCasesTest {
    private val reports = FakeReports()

    @Test
    fun noticeShowsDuringTheLastThreeDaysOfA31DayMonth() {
        assertTrue(ReportCalendar.arrivedPeriods(LocalDate(2026, 10, 28)).isEmpty())
        assertEquals(listOf(month(2026, 10)), ReportCalendar.arrivedPeriods(LocalDate(2026, 10, 29)))
        assertEquals(listOf(month(2026, 10)), ReportCalendar.arrivedPeriods(LocalDate(2026, 10, 31)))
        assertTrue(ReportCalendar.arrivedPeriods(LocalDate(2026, 11, 1)).isEmpty())
    }

    @Test
    fun noticeFollowsTheLengthOfTheMonth() {
        assertTrue(ReportCalendar.arrivedPeriods(LocalDate(2026, 11, 27)).isEmpty())
        assertEquals(listOf(month(2026, 11)), ReportCalendar.arrivedPeriods(LocalDate(2026, 11, 28)))
        assertTrue(ReportCalendar.arrivedPeriods(LocalDate(2026, 2, 25)).isEmpty())
        assertEquals(listOf(month(2026, 2)), ReportCalendar.arrivedPeriods(LocalDate(2026, 2, 26)))
        assertTrue(ReportCalendar.arrivedPeriods(LocalDate(2028, 2, 26)).isEmpty())
        assertEquals(listOf(month(2028, 2)), ReportCalendar.arrivedPeriods(LocalDate(2028, 2, 27)))
        assertEquals(listOf(month(2028, 2)), ReportCalendar.arrivedPeriods(LocalDate(2028, 2, 29)))
    }

    @Test
    fun decemberAlsoAnnouncesTheYear() {
        assertTrue(ReportCalendar.arrivedPeriods(LocalDate(2026, 12, 28)).isEmpty())
        assertEquals(listOf(month(2026, 12), ReviewPeriod.Year(2026)), ReportCalendar.arrivedPeriods(LocalDate(2026, 12, 29)))
        assertEquals(listOf(month(2026, 12), ReviewPeriod.Year(2026)), ReportCalendar.arrivedPeriods(LocalDate(2026, 12, 31)))
    }

    @Test
    fun historyCandidatesAreTheElapsedMonthsAndYears() {
        val today = LocalDate(2026, 11, 17)
        val months = ReportCalendar.historyMonths(today)
        assertEquals(24, months.size)
        assertEquals(YearMonth(2026, 10), months.first())
        assertEquals(YearMonth(2024, 11), months.last())
        assertEquals(listOf(2025, 2024, 2023), ReportCalendar.historyYears(today))
        assertEquals(DateSpan(LocalDate(2023, 1, 1), LocalDate(2026, 10, 31)), ReportCalendar.historyWindow(today))
        val january = LocalDate(2027, 1, 3)
        assertEquals(YearMonth(2026, 12), ReportCalendar.historyMonths(january).first())
        assertEquals(DateSpan(LocalDate(2024, 1, 1), LocalDate(2026, 12, 31)), ReportCalendar.historyWindow(january))
    }

    @Test
    fun historyKeepsPeriodsWithExpensesGroupedByYearAnnualFirst() {
        val today = LocalDate(2026, 11, 17)
        val withExpenses =
            setOf(
                YearMonth(2026, 11),
                YearMonth(2026, 10),
                YearMonth(2026, 3),
                YearMonth(2025, 12),
                YearMonth(2025, 1),
                YearMonth(2024, 10),
                YearMonth(2023, 6),
                YearMonth(2022, 5),
            )
        assertEquals(
            listOf(
                ReportYearGroup(2026, listOf(month(2026, 10), month(2026, 3))),
                ReportYearGroup(2025, listOf(ReviewPeriod.Year(2025), month(2025, 12), month(2025, 1))),
                ReportYearGroup(2024, listOf(ReviewPeriod.Year(2024))),
                ReportYearGroup(2023, listOf(ReviewPeriod.Year(2023))),
            ),
            ReportCalendar.history(today, withExpenses),
        )
        assertTrue(ReportCalendar.history(today, emptySet()).isEmpty())
    }

    @Test
    fun periodKeysRoundTrip() {
        assertEquals("2026-08", ReportCalendar.key(month(2026, 8)))
        assertEquals("2025", ReportCalendar.key(ReviewPeriod.Year(2025)))
        assertEquals(month(2026, 8), ReportCalendar.parseKey(" 2026-08 "))
        assertEquals(ReviewPeriod.Year(2025), ReportCalendar.parseKey("2025"))
        assertNull(ReportCalendar.parseKey("2026-13"))
        assertNull(ReportCalendar.parseKey("août"))
    }

    @Test
    fun arrivedReportsSkipViewedPeriodsAndDoNotReadOutsideTheWindow() =
        runTest {
            val find = FindArrivedReports(reports)
            assertEquals(AppResult.Success(emptyList()), find(LocalDate(2026, 12, 10)))
            assertEquals(0, reports.viewedReads)
            reports.viewed = AppResult.Success(setOf(month(2026, 12)))
            assertEquals(AppResult.Success(listOf<ReviewPeriod>(ReviewPeriod.Year(2026))), find(LocalDate(2026, 12, 30)))
            reports.viewed = AppResult.Failure(AppError.Network.Offline)
            assertEquals(AppResult.Failure(AppError.Network.Offline), find(LocalDate(2026, 12, 30)))
        }

    @Test
    fun markingAViewDelegatesToTheRepository() =
        runTest {
            MarkReportViewed(reports)(ReviewPeriod.Year(2026))
            assertEquals(listOf<ReviewPeriod>(ReviewPeriod.Year(2026)), reports.marked)
        }

    @Test
    fun historyReadsTheWindowOnce() =
        runTest {
            reports.months = AppResult.Success(setOf(YearMonth(2026, 10)))
            val groups = ListReportHistory(reports)(LocalDate(2026, 11, 17)).getOrNull()!!
            assertEquals(listOf(ReportYearGroup(2026, listOf(month(2026, 10)))), groups)
            assertEquals(listOf(DateSpan(LocalDate(2023, 1, 1), LocalDate(2026, 10, 31))), reports.spans)
        }

    @Test
    fun reportCarriesTheProfileCurrencyAndTheDelta() =
        runTest {
            val profiles = CurrencyProfiles(AppResult.Success(profile(Currency.CHF)))
            val reviewer = PeriodReviewer { AppResult.Success(review(it, total = 150_000, previous = 100_000)) }
            val sheet = LoadPeriodReport(reviewer, profiles)(month(2026, 10)).getOrNull()!!
            assertEquals(Currency.CHF, sheet.currency)
            assertEquals(50_000, sheet.deltaCents)
            profiles.result = AppResult.Failure(AppError.Network.Offline)
            val fallback = LoadPeriodReport(PeriodReviewer { AppResult.Success(review(it, 10, 0)) }, profiles)(month(2026, 10))
            assertEquals(Currency.Default, fallback.getOrNull()!!.currency)
            assertNull(fallback.getOrNull()!!.deltaCents)
            val failing = LoadPeriodReport(PeriodReviewer { AppResult.Failure(AppError.Server(500)) }, profiles)(month(2026, 10))
            assertEquals(AppResult.Failure(AppError.Server(500)), failing)
        }

    private fun month(
        year: Int,
        number: Int,
    ): ReviewPeriod = ReviewPeriod.Month(YearMonth(year, number))

    private fun review(
        period: ReviewPeriod,
        total: Long,
        previous: Long,
    ): PeriodReview = PeriodReview(period, period, total, previous, 0, 1, emptyList(), emptyList(), emptyList())

    private fun profile(currency: Currency): UserProfile =
        UserProfile(UserId("u-1"), null, null, null, null, currency, GlobalBudget(0, null, BudgetPeriod.MONTH), true, false)
}

private class FakeReports : ReportRepository {
    var viewed: AppResult<Set<ReviewPeriod>> = AppResult.Success(emptySet())
    var months: AppResult<Set<YearMonth>> = AppResult.Success(emptySet())
    var viewedReads = 0
    val marked = mutableListOf<ReviewPeriod>()
    val spans = mutableListOf<DateSpan>()

    override suspend fun viewedPeriods(): AppResult<Set<ReviewPeriod>> {
        viewedReads++
        return viewed
    }

    override suspend fun markViewed(period: ReviewPeriod): AppResult<Unit> {
        marked += period
        return AppResult.Success(Unit)
    }

    override suspend fun monthsWithConfirmedExpenses(span: DateSpan): AppResult<Set<YearMonth>> {
        spans += span
        return months
    }
}

private class CurrencyProfiles(
    var result: AppResult<UserProfile>,
) : ProfileRepository {
    override suspend fun bootstrap(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun getProfile(): AppResult<UserProfile> = result

    override suspend fun updateIdentity(identity: ProfileIdentity): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun updateBudget(budget: GlobalBudget): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun setDailyReminder(enabled: Boolean): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun updateCurrency(currency: Currency): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun completeOnboarding(monthlyBudgetCents: Long): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun checkPseudo(pseudo: String): AppResult<PseudoAvailability> =
        AppResult.Success(PseudoAvailability(true, emptyList()))
}
