package app.nemla.feature.dashboard.logic

import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.Category
import app.nemla.core.domain.model.CategoryBudget
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.ChargeFrequency
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.model.Expense
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.PeriodCursor
import app.nemla.core.domain.model.PeriodKind
import app.nemla.core.domain.model.RecurringCharge
import app.nemla.core.domain.usecase.PendingReviews
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

internal data class DashboardInputs(
    val today: LocalDate,
    val cursor: PeriodCursor,
    val categories: List<Category> = emptyList(),
    val yearExpenses: List<Expense> = emptyList(),
    val currentMonthExpenses: List<Expense> = emptyList(),
    val pendingExpenses: List<Expense> = emptyList(),
    val recurring: List<RecurringCharge> = emptyList(),
    val budget: GlobalBudget = GlobalBudget(0, null, BudgetPeriod.MONTH),
    val filter: Set<CategoryId>? = null,
    val deferredDebit: DeferredDebit = DeferredDebit.Immediate,
    val todayPage: Int = 0,
) {
    override fun toString(): String = "DashboardInputs(cursor=$cursor, expenses=${yearExpenses.size})"
}

internal object DashboardCalculator {
    private const val TICK_COUNT = 5
    private const val MONTHS = 12

    fun compute(inputs: DashboardInputs): DashboardView {
        val categoriesById = inputs.categories.associateBy { it.id }
        val ids = inputs.categories.map { it.id }
        val year = inputs.cursor.year
        val yearConfirmed = inputs.yearExpenses.filter { it.confirmed && it.date.year == year }
        val periodConfirmed = yearConfirmed.filter { it.date in inputs.cursor.span }
        val filteredPeriod = periodConfirmed.filter { CategoryFilters.accepts(inputs.filter, ids, it.categoryId) }
        val filteredYear = yearConfirmed.filter { CategoryFilters.accepts(inputs.filter, ids, it.categoryId) }
        val todayRows =
            inputs.currentMonthExpenses
                .filter { it.confirmed && it.date == inputs.today }
                .map { it.toRow(categoriesById) }
        return DashboardView(
            pending = PendingReviews.group(inputs.pendingExpenses).map { PendingGroup(it, it.drafts.sumOf { draft -> draft.amountCents }) },
            alerts = alerts(inputs.categories, inputs.currentMonthExpenses.filter { it.confirmed }),
            pilot = pilot(inputs, ids, periodConfirmed, filteredPeriod, filteredYear),
            spending =
                SpendingView(
                    groups = groups(filteredPeriod, categoriesById),
                    budgets = budgets(inputs.categories, periodConfirmed, inputs.cursor.kind),
                ),
            today = TodayView(todayRows, todayRows.sumOf { it.amountCents }, inputs.todayPage),
            todayDebitDate = if (inputs.deferredDebit.enabled) inputs.deferredDebit.debitDateOf(inputs.today) else null,
        )
    }

    private fun pilot(
        inputs: DashboardInputs,
        ids: List<CategoryId>,
        periodConfirmed: List<Expense>,
        filteredPeriod: List<Expense>,
        filteredYear: List<Expense>,
    ): PilotView {
        val monthly = inputs.budget.monthlyCents
        val annual = inputs.budget.annualCents?.takeIf { it != 0L } ?: (monthly * MONTHS)
        val kind = inputs.cursor.kind
        val budget = if (kind == PeriodKind.YEAR) annual else monthly
        val chart = chart(inputs, ids, filteredYear)
        val yearSpent = chart.bars.sumOf { it.totalCents }
        val weather =
            when (kind) {
                PeriodKind.MONTH -> {
                    val raw = periodConfirmed.sumOf { it.amountCents }
                    if (monthly > 0 && raw > 0) WeatherView(weatherOf(raw, monthly), kind, raw, monthly) else null
                }

                PeriodKind.YEAR -> {
                    if (annual > 0 && yearSpent > 0) WeatherView(weatherOf(yearSpent, annual), kind, yearSpent, annual) else null
                }
            }
        return PilotView(
            kind = kind,
            spentCents = filteredPeriod.sumOf { it.amountCents },
            budgetCents = budget,
            weather = weather,
            monthlyRecurringCents = monthlyRecurring(inputs.recurring),
            chart = chart,
            averageCents = roundedDivision(yearSpent, MONTHS.toLong()),
            filter = filterView(inputs.categories, inputs.filter),
        )
    }

    fun weatherOf(
        spentCents: Long,
        budgetCents: Long,
    ): Weather =
        when {
            spentCents > budgetCents -> Weather.STORM
            spentCents * 10 > budgetCents * 9 -> Weather.RAIN
            spentCents * 10 > budgetCents * 7 -> Weather.CLOUD
            else -> Weather.SUN
        }

    fun monthlyRecurring(charges: List<RecurringCharge>): Long {
        val monthly = charges.filter { it.frequency == ChargeFrequency.MONTHLY }.sumOf { it.amountCents }
        val yearly = charges.filter { it.frequency == ChargeFrequency.YEARLY }.sumOf { it.amountCents }
        return monthly + roundedDivision(yearly, MONTHS.toLong())
    }

    fun plannedMonthly(charges: List<RecurringCharge>): Long =
        charges.filter { it.frequency == ChargeFrequency.MONTHLY }.sumOf { it.amountCents }

    private fun chart(
        inputs: DashboardInputs,
        ids: List<CategoryId>,
        filteredYear: List<Expense>,
    ): ChartView {
        val year = inputs.cursor.year
        val today = inputs.today
        val projection = CategoryFilters.isAll(inputs.filter, ids) && year >= today.year
        val planned = plannedMonthly(inputs.recurring)
        val totals = filteredYear.groupBy { it.date.month.number }.mapValues { (_, list) -> list.sumOf { it.amountCents } }
        val bars =
            (1..MONTHS).map { month ->
                val future = year > today.year || (year == today.year && month > today.month.number)
                ChartBar(
                    monthNumber = month,
                    totalCents = totals[month] ?: 0,
                    plannedCents = if (projection && future) planned else 0,
                    selected = inputs.cursor.kind == PeriodKind.MONTH && inputs.cursor.monthNumber == month,
                )
            }
        val budgetLine = inputs.budget.monthlyCents.takeIf { it > 0 }
        val highest = maxOf(bars.maxOf { it.stackedCents }, budgetLine ?: 0)
        val axisMax = if (highest <= 0) 0 else (highest * 11 + 9) / 10
        val ticks = if (axisMax == 0L) listOf(0L) else (0 until TICK_COUNT).map { roundedDivision(axisMax * it, (TICK_COUNT - 1).toLong()) }
        return ChartView(
            year = year,
            bars = bars,
            budgetLineCents = budgetLine,
            axisMaxCents = axisMax,
            ticks = ticks,
            showsPlanned = planned > 0,
        )
    }

    private fun filterView(
        categories: List<Category>,
        filter: Set<CategoryId>?,
    ): FilterView {
        val ids = categories.map { it.id }
        val count = CategoryFilters.selectedCount(filter, ids)
        val summary =
            when {
                CategoryFilters.isAll(filter, ids) -> FilterSummary.ALL
                count == 0 -> FilterSummary.NONE
                else -> FilterSummary.SOME
            }
        return FilterView(
            summary = summary,
            selectedCount = count,
            options = categories.map { FilterOption(it.id, it.name, it.colorHex, filter == null || it.id in filter) },
        )
    }

    private fun groups(
        expenses: List<Expense>,
        categoriesById: Map<CategoryId, Category>,
    ): List<SpendingGroup> {
        val grouped = LinkedHashMap<String, MutableList<Expense>>()
        expenses.forEach { expense ->
            val category = expense.categoryId?.let(categoriesById::get)
            val key = if (category == null || !category.visible) SpendingGroup.OTHER_GROUP else category.id.value
            grouped.getOrPut(key) { mutableListOf() } += expense
        }
        return grouped
            .map { (key, list) ->
                val category = if (key == SpendingGroup.OTHER_GROUP) null else categoriesById[CategoryId(key)]
                SpendingGroup(
                    key = key,
                    name = category?.name,
                    colorHex = category?.colorHex,
                    totalCents = list.sumOf { it.amountCents },
                    rows = list.map { it.toRow(categoriesById) },
                )
            }.sortedByDescending { it.totalCents }
    }

    fun allocation(
        budget: CategoryBudget,
        kind: PeriodKind,
    ): Long {
        val (monthly, yearly) =
            when (budget) {
                CategoryBudget.None -> {
                    0L to 0L
                }

                is CategoryBudget.Monthly -> {
                    budget.cents to budget.cents * MONTHS
                }

                is CategoryBudget.Annual -> {
                    val monthly = roundedDivision(budget.cents, MONTHS.toLong())
                    monthly to if (budget.cents != 0L) budget.cents else monthly * MONTHS
                }
            }
        return if (kind == PeriodKind.YEAR) yearly else monthly
    }

    private fun budgets(
        categories: List<Category>,
        periodConfirmed: List<Expense>,
        kind: PeriodKind,
    ): List<BudgetUsage> = usages(categories, periodConfirmed, kind).sortedWith(byUsageRatio)

    private fun alerts(
        categories: List<Category>,
        monthConfirmed: List<Expense>,
    ): List<BudgetUsage> =
        usages(categories, monthConfirmed, PeriodKind.MONTH)
            .filter { it.usedCents >= it.allocatedCents }
            .sortedWith(byUsageRatio)

    private fun usages(
        categories: List<Category>,
        expenses: List<Expense>,
        kind: PeriodKind,
    ): List<BudgetUsage> {
        val used = expenses.groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.amountCents } }
        return categories.mapNotNull { category ->
            val allocated = allocation(category.budget, kind)
            if (allocated <= 0) {
                null
            } else {
                BudgetUsage(category.id, category.name, category.colorHex, used[category.id] ?: 0, allocated)
            }
        }
    }

    private val byUsageRatio: Comparator<BudgetUsage> =
        Comparator { a, b ->
            crossProduct(b.usedCents, a.allocatedCents).compareTo(crossProduct(a.usedCents, b.allocatedCents))
        }

    private fun crossProduct(
        amount: Long,
        allocation: Long,
    ): Double = amount.toDouble() * allocation.toDouble()

    fun rows(
        expenses: List<Expense>,
        categories: List<Category>,
    ): List<ExpenseRow> {
        val byId = categories.associateBy { it.id }
        return expenses.map { it.toRow(byId) }
    }

    private fun Expense.toRow(categoriesById: Map<CategoryId, Category>): ExpenseRow {
        val category = categoryId?.let(categoriesById::get)
        return ExpenseRow(
            id = id,
            label = label,
            date = date,
            amountCents = amountCents,
            source = source,
            categoryName = category?.name,
            colorHex = category?.colorHex,
        )
    }

    fun roundedDivision(
        value: Long,
        divisor: Long,
    ): Long = (value + divisor / 2).floorDiv(divisor)
}
