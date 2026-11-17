package app.nemla.feature.reports

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.DateSpan
import app.nemla.core.domain.model.ReportYearGroup
import app.nemla.core.domain.model.ReviewPeriod
import app.nemla.core.domain.port.DeviceLanguage
import app.nemla.core.domain.port.PeriodReviewer
import app.nemla.core.domain.port.PreferencesRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.ReportRepository
import app.nemla.core.domain.usecase.FindArrivedReports
import app.nemla.core.domain.usecase.ListReportHistory
import app.nemla.core.domain.usecase.LoadPeriodReport
import app.nemla.core.domain.usecase.MarkReportViewed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportsViewModelsTest : MainDispatcherTest() {
    private val reviewer = FakeReviewer()
    private val reports = FakeReports()
    private var today = LocalDate(2026, 12, 30)
    private var language = AppLanguage.FR

    private fun dialog(period: ReviewPeriod = month(2026, 10)) =
        ReportDialogViewModel(period, LoadPeriodReport(reviewer, FakeProfiles()), { language })

    private fun notice() = ReportNoticeViewModel(FindArrivedReports(reports), MarkReportViewed(reports), { today }, { language })

    private fun history() = ReportHistoryViewModel(ListReportHistory(reports), { today }, { language })

    @Test
    fun dialogLoadsTheRequestedPeriodWithTheProfileCurrency() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            reviewer.gate = gate
            val vm = dialog(ReviewPeriod.Year(2025))
            assertEquals(ReportContent.Loading, vm.state.value.content)
            gate.complete(Unit)
            val loaded = assertIs<ReportContent.Loaded>(vm.state.value.content)
            assertEquals(Currency.CHF, loaded.sheet.currency)
            assertEquals(23_450, loaded.sheet.deltaCents)
            assertEquals(listOf<ReviewPeriod>(ReviewPeriod.Year(2025)), reviewer.calls)
        }

    @Test
    fun dialogFailureCanBeRetried() =
        runTest {
            reviewer.results += AppResult.Failure(AppError.Network.Offline)
            val vm = dialog()
            assertEquals(ReportContent.Failed, vm.state.value.content)
            vm.onAction(ReportDialogAction.RetryClicked)
            assertIs<ReportContent.Loaded>(vm.state.value.content)
            assertEquals(2, reviewer.calls.size)
            vm.onAction(ReportDialogAction.RetryClicked)
            assertEquals(2, reviewer.calls.size)
        }

    @Test
    fun noticeListsUnviewedArrivedPeriods() =
        runTest {
            reports.viewed = AppResult.Success(setOf(month(2026, 12)))
            val vm = notice()
            assertTrue(
                vm.state.value.periods
                    .isEmpty(),
            )
            vm.onAction(ReportNoticeAction.Started)
            assertEquals(listOf<ReviewPeriod>(ReviewPeriod.Year(2026)), vm.state.value.periods)
        }

    @Test
    fun noticeIsHiddenOutsideTheLastDaysAndOnError() =
        runTest {
            today = LocalDate(2026, 11, 20)
            val vm = notice()
            vm.onAction(ReportNoticeAction.Started)
            assertTrue(
                vm.state.value.periods
                    .isEmpty(),
            )
            today = LocalDate(2026, 11, 28)
            reports.viewed = AppResult.Failure(AppError.Network.Offline)
            vm.onAction(ReportNoticeAction.Started)
            assertTrue(
                vm.state.value.periods
                    .isEmpty(),
            )
            reports.viewed = AppResult.Success(emptySet())
            vm.onAction(ReportNoticeAction.Started)
            assertEquals(listOf(month(2026, 11)), vm.state.value.periods)
        }

    @Test
    fun tappingANoticeRecordsTheViewAndOpensTheReport() =
        runTest {
            val vm = notice()
            vm.onAction(ReportNoticeAction.Started)
            assertEquals(listOf(month(2026, 12), ReviewPeriod.Year(2026)), vm.state.value.periods)
            vm.onAction(ReportNoticeAction.NoticeClicked(month(2026, 12)))
            assertEquals(listOf(month(2026, 12)), reports.marked)
            assertEquals(month(2026, 12), vm.state.value.opened)
            assertEquals(listOf<ReviewPeriod>(ReviewPeriod.Year(2026)), vm.state.value.periods)
            vm.onAction(ReportNoticeAction.Started)
            assertEquals(listOf<ReviewPeriod>(ReviewPeriod.Year(2026)), vm.state.value.periods)
            vm.onAction(ReportNoticeAction.DialogClosed)
            assertNull(vm.state.value.opened)
            vm.onAction(ReportNoticeAction.NoticeClicked(month(2026, 12)))
            assertEquals(1, reports.marked.size)
        }

    @Test
    fun historyGroupsPeriodsAndOpensOne() =
        runTest {
            today = LocalDate(2026, 11, 17)
            reports.months = AppResult.Success(setOf(YearMonth(2026, 10), YearMonth(2025, 4)))
            val vm = history()
            assertEquals(listOf(DateSpan(LocalDate(2023, 1, 1), LocalDate(2026, 10, 31))), reports.spans)
            assertEquals(
                HistoryContent.Loaded(
                    listOf(
                        ReportYearGroup(2026, listOf(month(2026, 10))),
                        ReportYearGroup(2025, listOf(ReviewPeriod.Year(2025), month(2025, 4))),
                    ),
                ),
                vm.state.value.content,
            )
            vm.onAction(ReportHistoryAction.PeriodClicked(ReviewPeriod.Year(2025)))
            assertEquals(ReviewPeriod.Year(2025), vm.state.value.opened)
            vm.onAction(ReportHistoryAction.DialogClosed)
            assertNull(vm.state.value.opened)
        }

    @Test
    fun historyEmptyAndFailureStates() =
        runTest {
            val empty = history()
            assertEquals(HistoryContent.Loaded(emptyList()), empty.state.value.content)
            reports.months = AppResult.Failure(AppError.Network.Timeout)
            val failing = history()
            assertEquals(HistoryContent.Failed, failing.state.value.content)
            reports.months = AppResult.Success(setOf(YearMonth(2026, 11)))
            failing.onAction(ReportHistoryAction.RetryClicked)
            assertIs<HistoryContent.Loaded>(failing.state.value.content)
        }

    @Test
    fun languageIsReadAgainWhenTheScreenStarts() =
        runTest {
            val vm = history()
            language = AppLanguage.EN
            vm.onAction(ReportHistoryAction.Started)
            assertEquals(AppLanguage.EN, vm.state.value.language)
        }

    @Test
    fun koinModuleBuildsEveryViewModel() {
        val koin =
            koinApplication {
                modules(
                    module {
                        single<PeriodReviewer> { reviewer }
                        single<ReportRepository> { reports }
                        single<ProfileRepository> { FakeProfiles() }
                        single<PreferencesRepository> { FakePreferences() }
                        single<DeviceLanguage> { DeviceLanguage { AppLanguage.FR } }
                    },
                    featureReportsModule,
                )
            }.koin
        koin.get<ReportNoticeViewModel>()
        koin.get<ReportHistoryViewModel>()
        koin.get<ReportDialogViewModel> { parametersOf(month(2026, 10)) }
    }
}
