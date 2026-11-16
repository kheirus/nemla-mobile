package app.nemla.feature.dashboard

import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.CategoryBudget
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.ChargeFrequency
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.model.Expense
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.PeriodCursor
import app.nemla.core.domain.model.PeriodKind
import app.nemla.core.domain.model.RecurringCharge
import app.nemla.core.domain.model.ReviewOrigin
import app.nemla.feature.dashboard.logic.CategoryFilters
import app.nemla.feature.dashboard.logic.DashboardCalculator
import app.nemla.feature.dashboard.logic.DashboardInputs
import app.nemla.feature.dashboard.logic.FilterSummary
import app.nemla.feature.dashboard.logic.SpendingGroup
import app.nemla.feature.dashboard.logic.TodayView
import app.nemla.feature.dashboard.logic.Weather
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DashboardCalculatorTest {
    private val november = PeriodCursor.monthOf(testToday)
    private val year2026 = november.withKind(PeriodKind.YEAR)
    private val categories = listOf(groceries, leisure, hidden)

    private fun inputs(
        cursor: PeriodCursor = november,
        year: List<Expense> = emptyList(),
        monthlyBudget: Long = 0,
        annualBudget: Long? = null,
        filter: Set<CategoryId>? = null,
        recurring: List<RecurringCharge> = emptyList(),
        currentMonth: List<Expense> = emptyList(),
    ) = DashboardInputs(
        today = testToday,
        cursor = cursor,
        categories = categories,
        yearExpenses = year,
        currentMonthExpenses = currentMonth,
        recurring = recurring,
        budget = GlobalBudget(monthlyBudget, annualBudget, BudgetPeriod.MONTH),
        filter = filter,
    )

    private val sample =
        listOf(
            expense("a", 10_000, LocalDate(2026, 11, 2), groceries.id),
            expense("b", 5_000, LocalDate(2026, 11, 10), leisure.id),
            expense("c", 2_000, LocalDate(2026, 11, 12), hidden.id),
            expense("d", 1_000, LocalDate(2026, 11, 13), null),
            expense("e", 40_000, LocalDate(2026, 3, 4), groceries.id),
            expense("f", 999, LocalDate(2026, 11, 14), groceries.id, confirmed = false),
        )

    @Test
    fun weatherThresholdsAreStrict() {
        assertEquals(Weather.SUN, DashboardCalculator.weatherOf(70, 100))
        assertEquals(Weather.CLOUD, DashboardCalculator.weatherOf(71, 100))
        assertEquals(Weather.CLOUD, DashboardCalculator.weatherOf(90, 100))
        assertEquals(Weather.RAIN, DashboardCalculator.weatherOf(91, 100))
        assertEquals(Weather.RAIN, DashboardCalculator.weatherOf(100, 100))
        assertEquals(Weather.STORM, DashboardCalculator.weatherOf(101, 100))
    }

    @Test
    fun monthSpentFollowsTheFilterButTheMonthWeatherDoesNot() {
        val view = DashboardCalculator.compute(inputs(year = sample, monthlyBudget = 20_000, filter = setOf(leisure.id)))
        assertEquals(5_000, view.pilot.spentCents)
        val weather = view.pilot.weather!!
        assertEquals(18_000, weather.spentCents)
        assertEquals(Weather.CLOUD, weather.weather)
        assertEquals(2_000, weather.remainingCents)
        assertEquals(0.25f, view.pilot.progress)
    }

    @Test
    fun yearWeatherUsesTheFilteredTotalAndTheAnnualBudgetOrTwelveMonths() {
        val filtered =
            DashboardCalculator.compute(
                inputs(cursor = year2026, year = sample, monthlyBudget = 5_000, filter = setOf(groceries.id)),
            )
        assertEquals(50_000, filtered.pilot.spentCents)
        assertEquals(60_000, filtered.pilot.budgetCents)
        assertEquals(50_000, filtered.pilot.weather!!.spentCents)
        assertEquals(Weather.CLOUD, filtered.pilot.weather!!.weather)
        val annual = DashboardCalculator.compute(inputs(cursor = year2026, year = sample, monthlyBudget = 5_000, annualBudget = 40_000))
        assertEquals(40_000, annual.pilot.budgetCents)
        assertEquals(Weather.STORM, annual.pilot.weather!!.weather)
    }

    @Test
    fun weatherIsHiddenWithoutBudgetOrSpending() {
        assertNull(DashboardCalculator.compute(inputs(year = sample)).pilot.weather)
        assertNull(DashboardCalculator.compute(inputs(monthlyBudget = 10_000)).pilot.weather)
        assertNull(DashboardCalculator.compute(inputs(year = sample)).pilot.progress)
    }

    @Test
    fun recurringChargesCountAnnualOnesForATwelfth() {
        val charges =
            listOf(charge("r1", 85_000), charge("r2", 24_000, ChargeFrequency.YEARLY), charge("r3", 1_001, ChargeFrequency.YEARLY))
        assertEquals(85_000 + 2_083, DashboardCalculator.monthlyRecurring(charges))
        assertEquals(85_000, DashboardCalculator.plannedMonthly(charges))
    }

    @Test
    fun chartTotalsPlannedBarsBudgetLineAndAxis() {
        val view =
            DashboardCalculator.compute(
                inputs(
                    year = sample,
                    monthlyBudget = 30_000,
                    recurring = listOf(charge("r1", 10_000), charge("r2", 12_000, ChargeFrequency.YEARLY)),
                ),
            )
        val chart = view.pilot.chart
        assertEquals(2026, chart.year)
        assertEquals(40_000, chart.bars[2].totalCents)
        assertEquals(18_000, chart.bars[10].totalCents)
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 10_000L), chart.bars.map { it.plannedCents })
        assertEquals(listOf(11), chart.bars.filter { it.selected }.map { it.monthNumber })
        assertEquals(30_000, chart.budgetLineCents)
        assertEquals(44_000, chart.axisMaxCents)
        assertEquals(listOf(0L, 11_000L, 22_000L, 33_000L, 44_000L), chart.ticks)
        assertTrue(chart.showsPlanned)
        assertEquals(4_833, view.pilot.averageCents)
    }

    @Test
    fun noProjectionForPartialFiltersOrPastYears() {
        val charges = listOf(charge("r1", 10_000))
        val partial = DashboardCalculator.compute(inputs(year = sample, filter = setOf(groceries.id), recurring = charges))
        assertTrue(
            partial.pilot.chart.bars
                .all { it.plannedCents == 0L },
        )
        val past = DashboardCalculator.compute(inputs(cursor = PeriodCursor.monthOf(LocalDate(2025, 5, 1)), recurring = charges))
        assertTrue(
            past.pilot.chart.bars
                .all { it.plannedCents == 0L },
        )
        val future = DashboardCalculator.compute(inputs(cursor = PeriodCursor.monthOf(LocalDate(2027, 5, 1)), recurring = charges))
        assertTrue(
            future.pilot.chart.bars
                .all { it.plannedCents == 10_000L },
        )
    }

    @Test
    fun emptyChartHasASingleZeroTick() {
        val chart = DashboardCalculator.compute(inputs()).pilot.chart
        assertEquals(0, chart.axisMaxCents)
        assertEquals(listOf(0L), chart.ticks)
        assertNull(chart.budgetLineCents)
        assertFalse(chart.showsPlanned)
    }

    @Test
    fun yearModeSelectsNoBar() {
        assertTrue(
            DashboardCalculator
                .compute(inputs(cursor = year2026))
                .pilot.chart.bars
                .none { it.selected },
        )
    }

    @Test
    fun breakdownGroupsHiddenAndUncategorizedIntoOther() {
        val groups = DashboardCalculator.compute(inputs(year = sample)).spending.groups
        assertEquals(listOf(groceries.id.value, leisure.id.value, SpendingGroup.OTHER_GROUP), groups.map { it.key })
        assertEquals(listOf(10_000L, 5_000L, 3_000L), groups.map { it.totalCents })
        assertTrue(groups.last().isOther)
        assertEquals(listOf("c", "d"), groups.last().rows.map { it.id.value })
        val filtered = DashboardCalculator.compute(inputs(year = sample, filter = setOf(leisure.id))).spending.groups
        assertEquals(listOf(leisure.id.value), filtered.map { it.key })
    }

    @Test
    fun categoryBudgetsIgnoreTheFilterAndSortByUsage() {
        val withBudgets =
            listOf(
                groceries.copy(budget = CategoryBudget.Monthly(20_000)),
                leisure.copy(budget = CategoryBudget.Annual(24_000)),
                hidden,
            )
        val view =
            DashboardCalculator.compute(inputs(year = sample, filter = setOf(leisure.id)).copy(categories = withBudgets))
        assertEquals(listOf(leisure.id, groceries.id), view.spending.budgets.map { it.categoryId })
        assertEquals(listOf(2_000L, 20_000L), view.spending.budgets.map { it.allocatedCents })
        assertTrue(
            view.spending.budgets
                .first()
                .isOver,
        )
        assertEquals(
            3_000,
            view.spending.budgets
                .first()
                .overCents,
        )
        val yearly = DashboardCalculator.compute(inputs(cursor = year2026, year = sample).copy(categories = withBudgets))
        assertEquals(
            listOf(240_000L, 24_000L),
            yearly.spending.budgets
                .sortedBy { it.name }
                .map { it.allocatedCents },
        )
    }

    @Test
    fun allocationRules() {
        assertEquals(1_000, DashboardCalculator.allocation(CategoryBudget.Annual(12_000), PeriodKind.MONTH))
        assertEquals(12_000, DashboardCalculator.allocation(CategoryBudget.Annual(12_000), PeriodKind.YEAR))
        assertEquals(60_000, DashboardCalculator.allocation(CategoryBudget.Monthly(5_000), PeriodKind.YEAR))
        assertEquals(0, DashboardCalculator.allocation(CategoryBudget.None, PeriodKind.YEAR))
    }

    @Test
    fun alertsUseTheCurrentCalendarMonthAndReachedBudgetsOnly() {
        val withBudgets =
            listOf(groceries.copy(budget = CategoryBudget.Monthly(10_000)), leisure.copy(budget = CategoryBudget.Monthly(9_000)))
        val view =
            DashboardCalculator.compute(
                inputs(cursor = PeriodCursor.monthOf(LocalDate(2025, 1, 1)), currentMonth = sample.filter { it.date.month.ordinal == 10 })
                    .copy(categories = withBudgets),
            )
        assertEquals(listOf(groceries.id), view.alerts.map { it.categoryId })
        assertEquals(0, view.alerts.single().overCents)
    }

    @Test
    fun todayListIsPaginatedFiveByFive() {
        val today = (1..12).map { expense("t$it", 100L * it) }
        val view = DashboardCalculator.compute(inputs(currentMonth = today + expense("y", 5, LocalDate(2026, 11, 15))).copy(todayPage = 2))
        assertEquals(12, view.today.rows.size)
        assertEquals(7_800, view.today.totalCents)
        assertEquals(3, view.today.pageCount)
        assertEquals(listOf("t11", "t12"), view.today.pageRows.map { it.id.value })
        assertFalse(view.today.hasNext)
        assertTrue(view.today.hasPrevious)
        assertEquals(1, TodayView(emptyList(), 0, 4).pageCount)
        assertEquals(0, TodayView(emptyList(), 0, 4).currentPage)
    }

    @Test
    fun pendingGroupsSumTheirExpenses() {
        val pending =
            listOf(
                expense("p1", 300, confirmed = false, voice = "v"),
                expense("p2", 200, confirmed = false, voice = "v"),
                expense("i", 50, confirmed = false),
            )
        val view = DashboardCalculator.compute(inputs().copy(pendingExpenses = pending))
        assertEquals(listOf(ReviewOrigin.VOICE, ReviewOrigin.IMPORT), view.pending.map { it.origin })
        assertEquals(listOf(500L, 50L), view.pending.map { it.totalCents })
        assertEquals(listOf(2, 1), view.pending.map { it.count })
    }

    @Test
    fun deferredDebitDateOfToday() {
        assertNull(DashboardCalculator.compute(inputs()).todayDebitDate)
        val rule = DeferredDebit(true, 31, 5, null)
        assertEquals(LocalDate(2026, 12, 5), DashboardCalculator.compute(inputs().copy(deferredDebit = rule)).todayDebitDate)
    }

    @Test
    fun filterSummaryAndLogic() {
        val ids = categories.map { it.id }
        assertEquals(
            FilterSummary.ALL,
            DashboardCalculator
                .compute(inputs())
                .pilot.filter.summary,
        )
        assertEquals(
            FilterSummary.NONE,
            DashboardCalculator
                .compute(inputs(filter = emptySet()))
                .pilot.filter.summary,
        )
        val some = DashboardCalculator.compute(inputs(filter = setOf(leisure.id))).pilot.filter
        assertEquals(FilterSummary.SOME, some.summary)
        assertEquals(1, some.selectedCount)
        assertEquals(listOf(false, true, false), some.options.map { it.selected })
        assertEquals(setOf(leisure.id, hidden.id), CategoryFilters.toggle(null, groceries.id, ids))
        assertEquals(setOf(leisure.id, groceries.id), CategoryFilters.toggle(setOf(leisure.id), groceries.id, ids))
        assertEquals(emptySet(), CategoryFilters.toggleAll(null, ids))
        assertEquals(emptySet(), CategoryFilters.toggleAll(ids.toSet(), ids))
        assertNull(CategoryFilters.toggleAll(setOf(leisure.id), ids))
        assertTrue(CategoryFilters.accepts(ids.toSet(), ids, null))
        assertFalse(CategoryFilters.accepts(setOf(leisure.id), ids, null))
        assertEquals(
            setOf(leisure.id, CategoryId("new")),
            CategoryFilters.withNewCategories(setOf(leisure.id), ids, ids + CategoryId("new")),
        )
        assertNull(CategoryFilters.withNewCategories(null, ids, ids + CategoryId("new")))
        assertEquals(0, DashboardCalculator.compute(inputs(year = sample, filter = emptySet())).pilot.spentCents)
    }

    @Test
    fun viewsNeverPrintAmounts() {
        val view = DashboardCalculator.compute(inputs(year = sample, monthlyBudget = 20_000))
        assertFalse(view.toString().contains("10000"))
        assertFalse(view.toString().contains("Courses"))
    }
}
