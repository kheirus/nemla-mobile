package app.nemla.feature.dashboard

import app.cash.turbine.test
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.model.ExpenseEdit
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ExpenseQuery
import app.nemla.core.domain.model.PaymentMethod
import app.nemla.core.domain.model.PeriodKind
import app.nemla.core.domain.model.ReviewOrigin
import app.nemla.core.domain.port.AuthRepository
import app.nemla.core.domain.port.CategoryRepository
import app.nemla.core.domain.port.DeferredDebitRepository
import app.nemla.core.domain.port.ExpenseRepository
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.RecurringChargeMaterializer
import app.nemla.core.domain.port.RecurringChargeRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashboardViewModelTest : MainDispatcherTest() {
    private val harness = DashboardHarness()

    private fun seed() {
        harness.profiles.result = AppResult.Success(profile(monthlyCents = 20_000, currency = Currency.CHF))
        harness.expenses.years[2026] =
            listOf(
                expense("a", 10_000, testToday, groceries.id),
                expense("b", 5_000, LocalDate(2026, 11, 3), leisure.id),
                expense("m", 30_000, LocalDate(2026, 3, 8), groceries.id),
            )
        harness.expenses.years[2025] = listOf(expense("old", 7_000, LocalDate(2025, 11, 2), groceries.id))
        harness.expenses.month =
            harness.expenses.years
                .getValue(2026)
                .filter { it.date.month == testToday.month }
        harness.expenses.pending =
            listOf(
                expense("p1", 1_200, confirmed = false, voice = "v-1"),
                expense("p2", 800, confirmed = false, voice = "v-1"),
                expense("i1", 300, confirmed = false),
            )
    }

    @Test
    fun initialLoadReadsEverythingOnceAndFillsTheBlocks() {
        seed()
        val vm = harness.viewModel()
        val state = vm.state.value
        assertFalse(state.isInitialLoading)
        assertNull(state.loadError)
        assertEquals("Léa", state.pseudo)
        assertEquals(Currency.CHF, state.currency)
        assertEquals(15_000, state.view.pilot.spentCents)
        assertEquals(20_000, state.view.pilot.budgetCents)
        assertEquals(listOf(ReviewOrigin.VOICE, ReviewOrigin.IMPORT), state.view.pending.map { it.origin })
        assertEquals(
            listOf("a"),
            state.view.today.rows
                .map { it.id.value },
        )
        assertEquals(1, harness.profiles.bootstraps)
        assertEquals(listOf(2026), harness.materializer.years)
        assertTrue(ExpenseQuery(confirmed = false) in harness.expenses.queries)
        assertTrue(
            ExpenseQuery(dateFrom = LocalDate(2026, 1, 1), dateTo = LocalDate(2026, 12, 31), confirmed = true) in harness.expenses.queries,
        )
        assertTrue(
            ExpenseQuery(dateFrom = LocalDate(2026, 11, 1), dateTo = LocalDate(2026, 11, 30), confirmed = true) in harness.expenses.queries,
        )
        assertEquals(listOf("c-1", "c-2", "c-3"), state.categories.map { it.id.value })
        assertFalse(state.toString().contains("Léa"))
    }

    @Test
    fun returningToTheForegroundRefreshesSilently() {
        seed()
        val vm = harness.viewModel()
        val before = harness.expenses.queries.size
        vm.onAction(DashboardAction.Started)
        assertEquals(before, harness.expenses.queries.size)
        harness.expenses.years[2026] = harness.expenses.years.getValue(2026) + expense("n", 1_000, testToday, leisure.id)
        vm.onAction(DashboardAction.Started)
        assertTrue(harness.expenses.queries.size > before)
        assertEquals(16_000, vm.state.value.view.pilot.spentCents)
        assertEquals(1, harness.profiles.bootstraps)
        assertFalse(vm.state.value.isRefreshing)
    }

    @Test
    fun changingTheMonthInsideTheYearNeedsNoNewRequest() {
        seed()
        val vm = harness.viewModel()
        val before = harness.expenses.queries.size
        repeat(8) { vm.onAction(DashboardAction.PreviousPeriodClicked) }
        assertEquals(YearMonth(2026, 3), vm.state.value.cursor.month)
        assertEquals(30_000, vm.state.value.view.pilot.spentCents)
        assertEquals(before, harness.expenses.queries.size)
    }

    @Test
    fun changingTheYearKeepsThePreviousDataUntilTheNewYearArrives() {
        seed()
        val vm = harness.viewModel()
        vm.onAction(DashboardAction.PeriodKindSelected(PeriodKind.YEAR))
        assertEquals(45_000, vm.state.value.view.pilot.spentCents)
        val gate = CompletableDeferred<Unit>()
        harness.expenses.yearGate = gate
        vm.onAction(DashboardAction.PreviousPeriodClicked)
        assertEquals(2025, vm.state.value.cursor.year)
        assertTrue(vm.state.value.isPeriodLoading)
        assertEquals(45_000, vm.state.value.view.pilot.spentCents)
        assertEquals(2026, vm.state.value.view.pilot.chart.year)
        gate.complete(Unit)
        assertFalse(vm.state.value.isPeriodLoading)
        assertEquals(7_000, vm.state.value.view.pilot.spentCents)
        assertEquals(2025, vm.state.value.view.pilot.chart.year)
        assertEquals(listOf(2026, 2025), harness.materializer.years)
    }

    @Test
    fun navigationIsUnboundedIntoTheFuture() {
        seed()
        val vm = harness.viewModel()
        vm.onAction(DashboardAction.PeriodKindSelected(PeriodKind.YEAR))
        repeat(3) { vm.onAction(DashboardAction.NextPeriodClicked) }
        assertEquals(2029, vm.state.value.cursor.year)
        assertEquals(0, vm.state.value.view.pilot.spentCents)
        assertNull(vm.state.value.loadError)
    }

    @Test
    fun tappingABarDrillsDownToThatMonth() {
        seed()
        val vm = harness.viewModel()
        vm.onAction(DashboardAction.PeriodKindSelected(PeriodKind.YEAR))
        vm.onAction(DashboardAction.ChartMonthTapped(3))
        val cursor = vm.state.value.cursor
        assertEquals(PeriodKind.MONTH, cursor.kind)
        assertEquals(YearMonth(2026, 3), cursor.month)
        assertEquals(
            listOf(3),
            vm.state.value.view.pilot.chart.bars
                .filter { it.selected }
                .map { it.monthNumber },
        )
        assertEquals(
            listOf("m"),
            vm.state.value.view.spending.groups
                .single()
                .rows
                .map { it.id.value },
        )
    }

    @Test
    fun categoryFilterChangesTheAmountButNotTheMonthWeather() {
        seed()
        val vm = harness.viewModel()
        vm.onAction(DashboardAction.FilterToggled(groceries.id))
        assertEquals(5_000, vm.state.value.view.pilot.spentCents)
        assertEquals(
            15_000,
            vm.state.value.view.pilot.weather
                ?.spentCents,
        )
        vm.onAction(DashboardAction.FilterAllToggled)
        assertEquals(15_000, vm.state.value.view.pilot.spentCents)
        vm.onAction(DashboardAction.FilterAllToggled)
        assertEquals(0, vm.state.value.view.pilot.spentCents)
    }

    @Test
    fun newCategoriesJoinAPartialSelection() {
        seed()
        val vm = harness.viewModel()
        vm.onAction(DashboardAction.FilterToggled(groceries.id))
        val added = category("c-9", "Nouvelle")
        harness.categories.result = AppResult.Success(listOf(groceries, leisure, hidden, added))
        vm.onAction(DashboardAction.PullToRefresh)
        val options = vm.state.value.view.pilot.filter.options
        assertEquals(listOf(false, true, true, true), options.map { it.selected })
    }

    @Test
    fun todayPaginationIsBounded() {
        seed()
        harness.expenses.month = (1..7).map { expense("t$it", 100, testToday) }
        val vm = harness.viewModel()
        vm.onAction(DashboardAction.TodayPreviousPage)
        assertEquals(0, vm.state.value.view.today.currentPage)
        vm.onAction(DashboardAction.TodayNextPage)
        vm.onAction(DashboardAction.TodayNextPage)
        assertEquals(1, vm.state.value.view.today.currentPage)
        assertEquals(
            listOf("t6", "t7"),
            vm.state.value.view.today.pageRows
                .map { it.id.value },
        )
    }

    @Test
    fun searchByDateIsClampedToTodayAndClearable() {
        seed()
        harness.expenses.search = listOf(expense("s1", 450, LocalDate(2026, 11, 2), leisure.id))
        val vm = harness.viewModel()
        vm.onAction(DashboardAction.SearchDateSelected(LocalDate(2027, 1, 1)))
        val search = vm.state.value.search
        assertEquals(testToday, search.date)
        assertEquals(listOf("s1"), search.results?.map { it.id.value })
        assertEquals("Loisirs", search.results?.single()?.categoryName)
        assertEquals(450, search.totalCents)
        assertEquals(ExpenseQuery(dateEquals = testToday, confirmed = true, limit = 50), harness.expenses.queries.last())
        vm.onAction(DashboardAction.SearchCleared)
        assertFalse(vm.state.value.search.isActive)
        assertNull(vm.state.value.search.results)
    }

    @Test
    fun failedSearchIsReported() {
        seed()
        val vm = harness.viewModel()
        harness.expenses.failure = AppError.Network.Offline
        vm.onAction(DashboardAction.SearchDateSelected(LocalDate(2026, 11, 2)))
        assertTrue(vm.state.value.search.failed)
    }

    @Test
    fun editingAnExpenseSendsTheChangesAndRefreshes() {
        seed()
        harness.debits.result = AppResult.Success(DeferredDebit(true, 31, 5, null))
        val vm = harness.viewModel()
        vm.onAction(DashboardAction.ExpenseClicked(ExpenseId("a")))
        val editor = assertNotNull(vm.state.value.editor)
        assertEquals("100,00", editor.amountText)
        assertEquals(LocalDate(2026, 12, 5), editor.debitDate)
        vm.onAction(DashboardAction.EditorAmountChanged("12,345"))
        vm.onAction(DashboardAction.EditorAmountChanged("42,5"))
        vm.onAction(DashboardAction.EditorDateSelected(LocalDate(2030, 1, 1)))
        vm.onAction(DashboardAction.EditorCategorySelected(null))
        vm.onAction(DashboardAction.EditorPaymentSelected(PaymentMethod.CASH))
        assertNull(
            vm.state.value.editor
                ?.debitDate,
        )
        val queriesBefore = harness.expenses.queries.size
        vm.onAction(DashboardAction.EditorSaveClicked)
        assertEquals(ExpenseId("a") to ExpenseEdit(4_250, testToday, null, PaymentMethod.CASH), harness.expenses.updates.single())
        assertNull(vm.state.value.editor)
        assertEquals(DashboardNotice.EXPENSE_UPDATED, vm.state.value.notice)
        assertTrue(harness.expenses.queries.size > queriesBefore)
        vm.onAction(DashboardAction.NoticeDismissed)
        assertNull(vm.state.value.notice)
    }

    @Test
    fun invalidAmountsAreRefusedLocally() {
        seed()
        val vm = harness.viewModel()
        vm.onAction(DashboardAction.ExpenseClicked(ExpenseId("a")))
        vm.onAction(DashboardAction.EditorAmountChanged(""))
        vm.onAction(DashboardAction.EditorSaveClicked)
        assertTrue(
            vm.state.value.editor!!
                .amountInvalid,
        )
        vm.onAction(DashboardAction.EditorAmountChanged("-3"))
        assertFalse(
            vm.state.value.editor!!
                .amountInvalid,
        )
        vm.onAction(DashboardAction.EditorSaveClicked)
        assertTrue(
            vm.state.value.editor!!
                .amountInvalid,
        )
        assertTrue(harness.expenses.updates.isEmpty())
    }

    @Test
    fun saveFailuresKeepTheEditorOpenWithATranslatedError() {
        seed()
        val vm = harness.viewModel()
        harness.expenses.updateResult = AppResult.Failure(AppError.Network.Offline)
        vm.onAction(DashboardAction.ExpenseClicked(ExpenseId("b")))
        vm.onAction(DashboardAction.EditorSaveClicked)
        assertEquals(
            EditorError.OFFLINE,
            vm.state.value.editor
                ?.error,
        )
        assertFalse(
            vm.state.value.editor!!
                .isSaving,
        )
        harness.expenses.updateResult = AppResult.Failure(AppError.NotFound)
        vm.onAction(DashboardAction.EditorSaveClicked)
        assertEquals(
            EditorError.SAVE_FAILED,
            vm.state.value.editor
                ?.error,
        )
        vm.onAction(DashboardAction.EditorDismissed)
        assertNull(vm.state.value.editor)
    }

    @Test
    fun deletionAsksForConfirmationFirst() {
        seed()
        val vm = harness.viewModel()
        vm.onAction(DashboardAction.ExpenseClicked(ExpenseId("m")))
        vm.onAction(DashboardAction.EditorDeleteClicked)
        assertTrue(
            vm.state.value.editor!!
                .confirmingDelete,
        )
        vm.onAction(DashboardAction.EditorDeleteCancelled)
        assertFalse(
            vm.state.value.editor!!
                .confirmingDelete,
        )
        assertTrue(harness.expenses.deletes.isEmpty())
        vm.onAction(DashboardAction.EditorDeleteClicked)
        vm.onAction(DashboardAction.EditorDeleteConfirmed)
        assertEquals(listOf(ExpenseId("m")), harness.expenses.deletes)
        assertNull(vm.state.value.editor)
        assertEquals(DashboardNotice.EXPENSE_DELETED, vm.state.value.notice)
    }

    @Test
    fun deleteFailureIsShownInTheEditor() {
        seed()
        harness.expenses.deleteResult = AppResult.Failure(AppError.Server(500))
        val vm = harness.viewModel()
        vm.onAction(DashboardAction.ExpenseClicked(ExpenseId("m")))
        vm.onAction(DashboardAction.EditorDeleteClicked)
        vm.onAction(DashboardAction.EditorDeleteConfirmed)
        assertEquals(
            EditorError.DELETE_FAILED,
            vm.state.value.editor
                ?.error,
        )
    }

    @Test
    fun unknownCategoriesAreClearedWhenEditing() {
        seed()
        harness.expenses.month = listOf(expense("x", 100, testToday, CategoryId("gone")))
        val vm = harness.viewModel()
        vm.onAction(DashboardAction.ExpenseClicked(ExpenseId("x")))
        assertNull(
            vm.state.value.editor
                ?.categoryId,
        )
        vm.onAction(DashboardAction.ExpenseClicked(ExpenseId("unknown")))
        assertEquals(
            ExpenseId("x"),
            vm.state.value.editor
                ?.id,
        )
    }

    @Test
    fun loadErrorsAreShownWithoutLosingDataAndCanBeRetried() {
        seed()
        val vm = harness.viewModel()
        harness.expenses.failure = AppError.Network.Timeout
        vm.onAction(DashboardAction.PullToRefresh)
        assertEquals(DashboardError.TIMEOUT, vm.state.value.loadError)
        assertEquals(15_000, vm.state.value.view.pilot.spentCents)
        assertFalse(vm.state.value.isRefreshing)
        harness.expenses.failure = null
        vm.onAction(DashboardAction.RetryClicked)
        assertNull(vm.state.value.loadError)
    }

    @Test
    fun firstLoadFailureEndsTheLoadingState() {
        harness.expenses.failure = AppError.Network.Offline
        harness.profiles.result = AppResult.Failure(AppError.Network.Offline)
        val vm = harness.viewModel()
        assertFalse(vm.state.value.isInitialLoading)
        assertEquals(DashboardError.OFFLINE, vm.state.value.loadError)
        assertNull(vm.state.value.pseudo)
        assertEquals(1, harness.profiles.bootstraps)
    }

    @Test
    fun pendingGroupsOpenTheReviewSheet() =
        runTest {
            seed()
            val vm = harness.viewModel()
            vm.effects.test {
                vm.onAction(DashboardAction.PendingGroupClicked(1))
                val effect = assertIs<DashboardEffect.OpenReview>(awaitItem())
                assertEquals(ReviewOrigin.IMPORT, effect.review.origin)
                assertEquals(listOf("i1"), effect.review.drafts.map { it.id?.value })
                vm.onAction(DashboardAction.PendingGroupClicked(9))
                expectNoEvents()
            }
        }

    @Test
    fun signOutEmitsSignedOutOrShowsAFailure() =
        runTest {
            val vm = harness.viewModel()
            vm.effects.test {
                vm.onAction(DashboardAction.SignOutClicked)
                assertEquals(DashboardEffect.SignedOut, awaitItem())
            }
            harness.auth.signOutResult = AppResult.Failure(AppError.Storage)
            vm.onAction(DashboardAction.SignOutClicked)
            assertTrue(vm.state.value.signOutFailed)
            assertEquals(2, harness.auth.signOuts)
        }

    @Test
    fun spendingTabFallsBackToBreakdownWithoutBudgets() {
        seed()
        val vm = harness.viewModel()
        vm.onAction(DashboardAction.SpendingTabSelected(SpendingTab.BUDGETS))
        assertEquals(SpendingTab.BREAKDOWN, vm.state.value.effectiveSpendingTab)
        vm.onAction(DashboardAction.GroupToggled(groceries.id.value))
        assertEquals(groceries.id.value, vm.state.value.expandedGroup)
        vm.onAction(DashboardAction.GroupToggled(groceries.id.value))
        assertNull(vm.state.value.expandedGroup)
    }

    @Test
    fun moduleResolvesTheViewModel() {
        val application =
            koinApplication {
                modules(
                    module {
                        single<ProfileRepository> { harness.profiles }
                        single<CategoryRepository> { harness.categories }
                        single<ExpenseRepository> { harness.expenses }
                        single<RecurringChargeRepository> { harness.recurring }
                        single<RecurringChargeMaterializer> { harness.materializer }
                        single<DeferredDebitRepository> { harness.debits }
                        single<AuthRepository> { harness.auth }
                    },
                    featureDashboardModule,
                )
            }
        try {
            assertFalse(
                application.koin
                    .get<DashboardViewModel>()
                    .state.value.isInitialLoading,
            )
        } finally {
            application.close()
        }
    }
}
