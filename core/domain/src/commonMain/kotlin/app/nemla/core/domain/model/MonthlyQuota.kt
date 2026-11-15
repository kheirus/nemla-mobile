package app.nemla.core.domain.model

import kotlinx.datetime.YearMonth

data class MonthlyQuota(
    val month: YearMonth,
    val voiceUsed: Int,
    val voiceLimit: Int,
    val importUsed: Int,
    val importLimit: Int,
) {
    val voiceRemaining: Int get() = (voiceLimit - voiceUsed).coerceAtLeast(0)
    val importRemaining: Int get() = (importLimit - importUsed).coerceAtLeast(0)
    val canDictate: Boolean get() = voiceRemaining > 0
    val canImport: Boolean get() = importRemaining > 0
}
