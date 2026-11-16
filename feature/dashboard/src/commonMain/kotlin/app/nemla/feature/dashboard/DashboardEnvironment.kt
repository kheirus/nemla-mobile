package app.nemla.feature.dashboard

import androidx.compose.ui.text.intl.Locale
import app.nemla.core.domain.model.AppLanguage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

internal fun interface UiLanguage {
    fun current(): AppLanguage
}

internal object SystemUiLanguage : UiLanguage {
    override fun current(): AppLanguage = AppLanguage.fromTag(Locale.current.language.lowercase())
}

internal fun interface DashboardClock {
    fun today(): LocalDate
}

internal object SystemDashboardClock : DashboardClock {
    override fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
}
