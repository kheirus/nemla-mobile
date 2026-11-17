package app.nemla.core.domain.usecase

import app.nemla.core.common.AppResult
import app.nemla.core.common.getOrNull
import app.nemla.core.common.map
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.ReportCalendar
import app.nemla.core.domain.model.ReportSheet
import app.nemla.core.domain.model.ReportYearGroup
import app.nemla.core.domain.model.ReviewPeriod
import app.nemla.core.domain.port.PeriodReviewer
import app.nemla.core.domain.port.ProfileRepository
import app.nemla.core.domain.port.ReportRepository
import kotlinx.datetime.LocalDate

class LoadPeriodReport(
    private val reviewer: PeriodReviewer,
    private val profiles: ProfileRepository,
) {
    suspend operator fun invoke(period: ReviewPeriod): AppResult<ReportSheet> {
        val currency = profiles.getProfile().getOrNull()?.currency ?: Currency.Default
        return reviewer.review(period).map { ReportSheet(it, currency) }
    }
}

class FindArrivedReports(
    private val reports: ReportRepository,
) {
    suspend operator fun invoke(today: LocalDate): AppResult<List<ReviewPeriod>> {
        val arrived = ReportCalendar.arrivedPeriods(today)
        if (arrived.isEmpty()) return AppResult.Success(emptyList())
        return reports.viewedPeriods().map { viewed -> arrived.filterNot { it in viewed } }
    }
}

class MarkReportViewed(
    private val reports: ReportRepository,
) {
    suspend operator fun invoke(period: ReviewPeriod): AppResult<Unit> = reports.markViewed(period)
}

class ListReportHistory(
    private val reports: ReportRepository,
) {
    suspend operator fun invoke(today: LocalDate): AppResult<List<ReportYearGroup>> =
        reports.monthsWithConfirmedExpenses(ReportCalendar.historyWindow(today)).map { months -> ReportCalendar.history(today, months) }
}
