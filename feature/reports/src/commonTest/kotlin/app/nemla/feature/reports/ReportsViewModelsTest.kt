package app.nemla.feature.reports

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.DateSpan
import app.nemla.core.domain.model.InboxChannel
import app.nemla.core.domain.model.InboxItemId
import app.nemla.core.domain.model.InboxKind
import app.nemla.core.domain.model.ReportSheet
import app.nemla.core.domain.model.ReportYearGroup
import app.nemla.core.domain.model.ReviewPeriod
import app.nemla.core.domain.port.DeviceLanguage
import app.nemla.core.domain.port.InboxRepository
import app.nemla.core.domain.port.PeriodReviewer
import app.nemla.core.domain.port.PreferencesRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.ReportRepository
import app.nemla.core.domain.usecase.DismissInboxItem
import app.nemla.core.domain.usecase.FindArrivedReports
import app.nemla.core.domain.usecase.ListInAppNotices
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportsViewModelsTest : MainDispatcherTest() {
    private val reviewer = FakeReviewer()
    private val reports = FakeReports()
    private var today = LocalDate(2026, 12, 30)
    private var language = AppLanguage.FR

    private fun dialog(period: ReviewPeriod = month(2026, 10)) =
        ReportDialogViewModel(ReportRequest.Compute(period), LoadPeriodReport(reviewer, FakeProfiles()), { language })

    private fun delivered(
        period: ReviewPeriod?,
        sheet: ReportSheet?,
    ) = ReportDialogViewModel(ReportRequest.Delivered(period, sheet), LoadPeriodReport(reviewer, FakeProfiles()), { language })

    private val inbox = FakeInbox()

    private fun banners() = InboxBannersViewModel(ListInAppNotices(inbox), DismissInboxItem(inbox), { language })

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
    fun deliveredReportsAreShownWithoutLoading() {
        val sheet = ReportSheet(sampleReview(month(2026, 10)), Currency.EUR)
        val vm = delivered(null, sheet)
        assertEquals(ReportContent.Loaded(sheet), vm.state.value.content)
        assertEquals(month(2026, 10), vm.state.value.period)
        assertTrue(reviewer.calls.isEmpty())
    }

    @Test
    fun aMissingDeliveredReportFailsAndRetriesOnlyWithAPeriod() {
        val orphan = delivered(null, null)
        assertEquals(ReportContent.Failed, orphan.state.value.content)
        assertFalse(orphan.state.value.canRetry)
        assertNull(orphan.state.value.period)
        orphan.onAction(ReportDialogAction.RetryClicked)
        assertEquals(ReportContent.Failed, orphan.state.value.content)
        assertTrue(reviewer.calls.isEmpty())
        val dated = delivered(ReviewPeriod.Year(2025), null)
        assertEquals(ReportContent.Failed, dated.state.value.content)
        assertTrue(dated.state.value.canRetry)
        assertTrue(reviewer.calls.isEmpty())
        dated.onAction(ReportDialogAction.RetryClicked)
        assertIs<ReportContent.Loaded>(dated.state.value.content)
        assertEquals(listOf<ReviewPeriod>(ReviewPeriod.Year(2025)), reviewer.calls)
    }

    @Test
    fun bannersListInAppItemsOnEachStart() =
        runTest {
            inbox.items = AppResult.Success(listOf(inboxItem("n-1"), inboxItem("p-1", channel = InboxChannel.PUSH)))
            val vm = banners()
            assertTrue(
                vm.state.value.items
                    .isEmpty(),
            )
            vm.onAction(InboxBannersAction.Started)
            assertEquals(
                listOf(InboxItemId("n-1")),
                vm.state.value.items
                    .map { it.id },
            )
            inbox.items = AppResult.Failure(AppError.Network.Offline)
            vm.onAction(InboxBannersAction.Started)
            assertEquals(
                listOf(InboxItemId("n-1")),
                vm.state.value.items
                    .map { it.id },
            )
            inbox.items = AppResult.Success(listOf(inboxItem("n-1"), inboxItem("n-2", kind = InboxKind.DAILY_REMINDER)))
            language = AppLanguage.EN
            vm.onAction(InboxBannersAction.Started)
            assertEquals(2, vm.state.value.items.size)
            assertEquals(AppLanguage.EN, vm.state.value.language)
        }

    @Test
    fun dismissingOrTappingAReminderMarksItSeen() =
        runTest {
            inbox.items = AppResult.Success(listOf(inboxItem("n-1"), inboxItem("r-1", kind = InboxKind.DAILY_REMINDER)))
            val vm = banners()
            vm.onAction(InboxBannersAction.Started)
            vm.onAction(InboxBannersAction.ItemClicked(InboxItemId("r-1")))
            assertNull(vm.state.value.opened)
            vm.onAction(InboxBannersAction.DismissClicked(InboxItemId("n-1")))
            assertEquals(listOf(InboxItemId("r-1"), InboxItemId("n-1")), inbox.seen)
            assertTrue(
                vm.state.value.items
                    .isEmpty(),
            )
            vm.onAction(InboxBannersAction.Started)
            assertTrue(
                vm.state.value.items
                    .isEmpty(),
            )
            vm.onAction(InboxBannersAction.DismissClicked(InboxItemId("n-1")))
            assertEquals(2, inbox.seen.size)
        }

    @Test
    fun tappingAReportOpensTheDeliveredSheetAndMarksItSeen() =
        runTest {
            val sheet = ReportSheet(sampleReview(month(2026, 10)), Currency.EUR)
            inbox.items = AppResult.Success(listOf(inboxItem("n-1", period = month(2026, 10), report = sheet)))
            val vm = banners()
            vm.onAction(InboxBannersAction.Started)
            vm.onAction(InboxBannersAction.ItemClicked(InboxItemId("n-1")))
            val opened = assertNotNull(vm.state.value.opened)
            assertEquals(InboxItemId("n-1"), opened.id)
            assertEquals(sheet, opened.request.sheet)
            assertEquals(month(2026, 10), opened.request.period)
            assertEquals(listOf(InboxItemId("n-1")), inbox.seen)
            assertTrue(
                vm.state.value.items
                    .isEmpty(),
            )
            vm.onAction(InboxBannersAction.DialogClosed)
            assertNull(vm.state.value.opened)
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
                        single<InboxRepository> { inbox }
                        factory { ListInAppNotices(get()) }
                        factory { DismissInboxItem(get()) }
                    },
                    featureReportsModule,
                )
            }.koin
        koin.get<ReportNoticeViewModel>()
        koin.get<ReportHistoryViewModel>()
        koin.get<InboxBannersViewModel>()
        koin.get<ReportDialogViewModel> { parametersOf(ReportRequest.Compute(month(2026, 10))) }
        koin.get<ReportDialogViewModel> { parametersOf(ReportRequest.Delivered(null, null)) }
    }
}
