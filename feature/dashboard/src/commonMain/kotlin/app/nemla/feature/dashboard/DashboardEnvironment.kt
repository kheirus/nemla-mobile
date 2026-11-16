package app.nemla.feature.dashboard

import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.usecase.CurrentLanguages
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

internal fun interface UiLanguage {
    fun current(): AppLanguage
}

internal class PreferredUiLanguage(
    private val languages: CurrentLanguages,
) : UiLanguage {
    override fun current(): AppLanguage = languages.ui()
}

internal fun interface DashboardClock {
    fun today(): LocalDate
}

internal object SystemDashboardClock : DashboardClock {
    override fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
}
