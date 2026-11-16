package app.nemla.core.domain.model

object BudgetRules {
    private const val MONTHS_PER_YEAR = 12L

    fun globalBudget(
        amountCents: Long,
        period: BudgetPeriod,
    ): GlobalBudget =
        when (period) {
            BudgetPeriod.YEAR -> GlobalBudget(roundedDiv(amountCents, MONTHS_PER_YEAR), amountCents, BudgetPeriod.YEAR)
            BudgetPeriod.MONTH -> GlobalBudget(amountCents, amountCents * MONTHS_PER_YEAR, BudgetPeriod.MONTH)
        }

    fun displayedAmount(budget: GlobalBudget): Long =
        when (budget.period) {
            BudgetPeriod.YEAR -> budget.annualCents ?: (budget.monthlyCents * MONTHS_PER_YEAR)
            BudgetPeriod.MONTH -> budget.monthlyCents
        }

    fun monthlyOf(
        amountCents: Long,
        period: BudgetPeriod,
    ): Long = if (period == BudgetPeriod.YEAR) roundedDiv(amountCents, MONTHS_PER_YEAR) else amountCents

    fun fixedMonthlyCents(charges: List<RecurringCharge>): Long {
        val monthly = charges.filter { it.frequency == ChargeFrequency.MONTHLY }.sumOf { it.amountCents }
        val yearly = charges.filter { it.frequency == ChargeFrequency.YEARLY }.sumOf { it.amountCents }
        return monthly + roundedDiv(yearly, MONTHS_PER_YEAR)
    }

    fun fixedYearlyCents(charges: List<RecurringCharge>): Long =
        charges.sumOf { if (it.frequency == ChargeFrequency.YEARLY) it.amountCents else it.amountCents * MONTHS_PER_YEAR }

    fun categoryBudget(
        amountCents: Long?,
        period: BudgetPeriod,
    ): CategoryBudget =
        when {
            amountCents == null -> CategoryBudget.None
            period == BudgetPeriod.YEAR -> CategoryBudget.Annual(amountCents)
            else -> CategoryBudget.Monthly(amountCents)
        }

    fun categoryEstimate(
        monthlyEstimateCents: Long,
        period: BudgetPeriod,
    ): Long = if (period == BudgetPeriod.YEAR) monthlyEstimateCents * MONTHS_PER_YEAR else monthlyEstimateCents

    fun roundedDiv(
        value: Long,
        divisor: Long,
    ): Long {
        val magnitude = if (value < 0) -value else value
        val rounded = (magnitude + divisor / 2) / divisor
        return if (value < 0) -rounded else rounded
    }
}
