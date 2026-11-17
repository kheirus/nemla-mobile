package app.nemla.feature.reports

import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.usecase.CurrentLanguages
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

internal fun interface ReportsClock {
    fun today(): LocalDate
}

internal object SystemReportsClock : ReportsClock {
    override fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
}

internal fun interface ReportsLanguage {
    fun current(): AppLanguage
}

internal class PreferredReportsLanguage(
    private val languages: CurrentLanguages,
) : ReportsLanguage {
    override fun current(): AppLanguage = languages.ui()
}
