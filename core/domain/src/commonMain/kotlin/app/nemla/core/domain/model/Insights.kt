package app.nemla.core.domain.model

import kotlinx.datetime.YearMonth

sealed interface EstimateBasis {
    data object FixedCharges : EstimateBasis

    data class Average(
        val months: Int,
    ) : EstimateBasis
}

data class MonthSpending(
    val month: YearMonth,
    val totalCents: Long,
)

data class CategoryEstimate(
    val categoryId: CategoryId,
    val monthlyCents: Long,
    val monthsObserved: Int,
)

data class BudgetEstimate(
    val monthlyCents: Long,
    val annualCents: Long,
    val fixedMonthlyCents: Long,
    val months: List<MonthSpending>,
    val perCategory: List<CategoryEstimate>,
    val basis: EstimateBasis,
)

sealed interface ReviewPeriod {
    data class Month(
        val month: YearMonth,
    ) : ReviewPeriod

    data class Year(
        val year: Int,
    ) : ReviewPeriod
}

data class CategoryShare(
    val name: String,
    val amountCents: Long,
    val sharePercent: Int,
    val variationCents: Long,
)

class PeriodReview(
    val period: ReviewPeriod,
    val previousPeriod: ReviewPeriod,
    val totalCents: Long,
    val previousTotalCents: Long,
    val budgetCents: Long,
    val expenseCount: Int,
    val categories: List<CategoryShare>,
    val highlights: List<String>,
    val advice: List<String>,
) {
    override fun equals(other: Any?): Boolean =
        other is PeriodReview &&
            other.period == period &&
            other.previousPeriod == previousPeriod &&
            other.totalCents == totalCents &&
            other.previousTotalCents == previousTotalCents &&
            other.budgetCents == budgetCents &&
            other.expenseCount == expenseCount &&
            other.categories == categories &&
            other.highlights == highlights &&
            other.advice == advice

    override fun hashCode(): Int =
        listOf(period, previousPeriod, totalCents, previousTotalCents, budgetCents, expenseCount, categories, highlights, advice).hashCode()

    override fun toString(): String = "PeriodReview(period=$period, categories=${categories.size})"
}

class AccountExport(
    val fileName: String,
    val mimeType: String,
    val content: ByteArray,
) {
    override fun toString(): String = "AccountExport(mimeType=$mimeType, ${content.size} bytes)"
}
