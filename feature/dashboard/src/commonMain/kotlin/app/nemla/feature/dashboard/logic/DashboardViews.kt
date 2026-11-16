package app.nemla.feature.dashboard.logic

import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ExpenseSource
import app.nemla.core.domain.model.PendingReview
import app.nemla.core.domain.model.PeriodKind
import app.nemla.core.domain.model.ReviewOrigin
import kotlinx.datetime.LocalDate

internal enum class Weather { SUN, CLOUD, RAIN, STORM }

internal data class WeatherView(
    val weather: Weather,
    val kind: PeriodKind,
    val spentCents: Long,
    val budgetCents: Long,
) {
    val remainingCents: Long get() = budgetCents - spentCents

    val progress: Float get() = ratio(spentCents, budgetCents)

    override fun toString(): String = "WeatherView(weather=$weather, kind=$kind)"
}

internal data class ChartBar(
    val monthNumber: Int,
    val totalCents: Long,
    val plannedCents: Long,
    val selected: Boolean,
) {
    val stackedCents: Long get() = totalCents + plannedCents

    override fun toString(): String = "ChartBar(month=$monthNumber, selected=$selected)"
}

internal data class ChartView(
    val year: Int,
    val bars: List<ChartBar>,
    val budgetLineCents: Long?,
    val axisMaxCents: Long,
    val ticks: List<Long>,
    val showsPlanned: Boolean,
) {
    override fun toString(): String = "ChartView(year=$year, bars=${bars.size})"
}

internal enum class FilterSummary { ALL, NONE, SOME }

internal data class FilterOption(
    val id: CategoryId,
    val name: String,
    val colorHex: String,
    val selected: Boolean,
)

internal data class FilterView(
    val summary: FilterSummary,
    val selectedCount: Int,
    val options: List<FilterOption>,
)

internal data class PilotView(
    val kind: PeriodKind,
    val spentCents: Long,
    val budgetCents: Long,
    val weather: WeatherView?,
    val monthlyRecurringCents: Long,
    val chart: ChartView,
    val averageCents: Long,
    val filter: FilterView,
) {
    val progress: Float? get() = if (budgetCents > 0) ratio(spentCents, budgetCents) else null

    override fun toString(): String = "PilotView(kind=$kind, filter=${filter.summary})"
}

internal data class ExpenseRow(
    val id: ExpenseId,
    val label: String?,
    val date: LocalDate,
    val amountCents: Long,
    val source: ExpenseSource,
    val categoryName: String?,
    val colorHex: String?,
) {
    override fun toString(): String = "ExpenseRow(id=${id.value})"
}

internal data class SpendingGroup(
    val key: String,
    val name: String?,
    val colorHex: String?,
    val totalCents: Long,
    val rows: List<ExpenseRow>,
) {
    val isOther: Boolean get() = key == OTHER_GROUP

    override fun toString(): String = "SpendingGroup(key=$key, rows=${rows.size})"

    companion object {
        const val OTHER_GROUP = "__other__"
    }
}

internal data class BudgetUsage(
    val categoryId: CategoryId,
    val name: String,
    val colorHex: String,
    val usedCents: Long,
    val allocatedCents: Long,
) {
    val overCents: Long get() = usedCents - allocatedCents

    val isOver: Boolean get() = usedCents > allocatedCents

    val progress: Float get() = ratio(usedCents, allocatedCents)

    override fun toString(): String = "BudgetUsage(category=${categoryId.value})"
}

internal data class SpendingView(
    val groups: List<SpendingGroup>,
    val budgets: List<BudgetUsage>,
) {
    val hasBudgets: Boolean get() = budgets.isNotEmpty()

    val totalCents: Long get() = groups.sumOf { it.totalCents }
}

internal data class PendingGroup(
    val review: PendingReview,
    val totalCents: Long,
) {
    val origin: ReviewOrigin get() = review.origin

    val count: Int get() = review.drafts.size

    override fun toString(): String = "PendingGroup(origin=$origin, count=$count)"
}

internal data class TodayView(
    val rows: List<ExpenseRow>,
    val totalCents: Long,
    val page: Int,
) {
    val pageCount: Int get() = maxOf(1, (rows.size + PAGE_SIZE - 1) / PAGE_SIZE)

    val currentPage: Int get() = page.coerceIn(0, pageCount - 1)

    val pageRows: List<ExpenseRow> get() = rows.drop(currentPage * PAGE_SIZE).take(PAGE_SIZE)

    val hasPrevious: Boolean get() = currentPage > 0

    val hasNext: Boolean get() = currentPage < pageCount - 1

    override fun toString(): String = "TodayView(rows=${rows.size}, page=$page)"

    companion object {
        const val PAGE_SIZE = 5
    }
}

internal data class DashboardView(
    val pending: List<PendingGroup>,
    val alerts: List<BudgetUsage>,
    val pilot: PilotView,
    val spending: SpendingView,
    val today: TodayView,
    val todayDebitDate: LocalDate?,
)

internal fun ratio(
    part: Long,
    whole: Long,
): Float = if (whole <= 0) 0f else (part.toDouble() / whole.toDouble()).toFloat().coerceIn(0f, 1f)
