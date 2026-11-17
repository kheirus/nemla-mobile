package app.nemla.core.domain.port

import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.DateSpan
import app.nemla.core.domain.model.ReviewPeriod
import kotlinx.datetime.YearMonth

interface ReportRepository {
    suspend fun viewedPeriods(): AppResult<Set<ReviewPeriod>>

    suspend fun markViewed(period: ReviewPeriod): AppResult<Unit>

    suspend fun monthsWithConfirmedExpenses(span: DateSpan): AppResult<Set<YearMonth>>
}
