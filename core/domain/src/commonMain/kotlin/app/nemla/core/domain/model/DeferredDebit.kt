package app.nemla.core.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth

data class DeferredDebit(
    val enabled: Boolean,
    val cutoffDay: Int,
    val debitDay: Int,
    val since: LocalDate?,
) {
    fun debitDateOf(purchase: LocalDate): LocalDate {
        if (!enabled) return purchase
        if (since != null && purchase < since) return purchase
        val purchaseMonth = purchase.yearMonth
        val cutoff = minOf(cutoffDay, purchaseMonth.numberOfDays)
        val closingMonth = if (purchase.day > cutoff) purchaseMonth.plusMonth() else purchaseMonth
        val target = closingMonth.plusMonth()
        val day = minOf(maxOf(1, debitDay), target.numberOfDays)
        return LocalDate(target.year, target.month.number, day)
    }

    companion object {
        const val DEFAULT_CUTOFF_DAY = 31
        const val DEFAULT_DEBIT_DAY = 5

        val Immediate: DeferredDebit = DeferredDebit(false, DEFAULT_CUTOFF_DAY, DEFAULT_DEBIT_DAY, null)
    }
}
