package app.nemla.core.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.yearMonth

data class ReportYearGroup(
    val year: Int,
    val periods: List<ReviewPeriod>,
)

class ReportSheet(
    val review: PeriodReview,
    val currency: Currency,
) {
    val deltaCents: Long? get() = if (review.previousTotalCents > 0) review.totalCents - review.previousTotalCents else null

    override fun equals(other: Any?): Boolean = other is ReportSheet && other.review == review && other.currency == currency

    override fun hashCode(): Int = listOf(review, currency).hashCode()

    override fun toString(): String = "ReportSheet(review=$review, currency=$currency)"
}

object ReportCalendar {
    const val HISTORY_MONTHS: Int = 24
    const val HISTORY_YEARS: Int = 3
    const val NOTICE_DAYS: Int = 3

    fun arrivedPeriods(today: LocalDate): List<ReviewPeriod> {
        val month = today.yearMonth
        if (today.day <= month.lastDay.day - NOTICE_DAYS) return emptyList()
        val monthly = ReviewPeriod.Month(month)
        return if (today.month == Month.DECEMBER) listOf(monthly, ReviewPeriod.Year(today.year)) else listOf(monthly)
    }

    fun historyMonths(today: LocalDate): List<YearMonth> =
        generateSequence(today.yearMonth.minusMonth()) { it.minusMonth() }.take(HISTORY_MONTHS).toList()

    fun historyYears(today: LocalDate): List<Int> = (1..HISTORY_YEARS).map { today.year - it }

    fun historyWindow(today: LocalDate): DateSpan =
        DateSpan(LocalDate(today.year - HISTORY_YEARS, 1, 1), today.yearMonth.minusMonth().lastDay)

    fun history(
        today: LocalDate,
        monthsWithExpenses: Set<YearMonth>,
    ): List<ReportYearGroup> {
        val months = historyMonths(today).filter { it in monthsWithExpenses }
        val years = historyYears(today).filter { year -> monthsWithExpenses.any { it.year == year } }
        val allYears = (months.map { it.year } + years).distinct().sortedDescending()
        return allYears.map { year ->
            val annual = if (year in years) listOf<ReviewPeriod>(ReviewPeriod.Year(year)) else emptyList()
            ReportYearGroup(year, annual + months.filter { it.year == year }.sortedDescending().map(ReviewPeriod::Month))
        }
    }

    fun key(period: ReviewPeriod): String =
        when (period) {
            is ReviewPeriod.Month -> period.month.toString()
            is ReviewPeriod.Year -> period.year.toString().padStart(YEAR_DIGITS, '0')
        }

    fun parseKey(value: String): ReviewPeriod? {
        val trimmed = value.trim()
        return when {
            YEAR_KEY.matches(trimmed) -> {
                ReviewPeriod.Year(trimmed.toInt())
            }

            MONTH_KEY.matches(trimmed) -> {
                try {
                    ReviewPeriod.Month(YearMonth.parse(trimmed))
                } catch (e: IllegalArgumentException) {
                    null
                }
            }

            else -> {
                null
            }
        }
    }

    private const val YEAR_DIGITS = 4
    private val YEAR_KEY = Regex("^[0-9]{4}$")
    private val MONTH_KEY = Regex("^[0-9]{4}-(0[1-9]|1[0-2])$")
}
