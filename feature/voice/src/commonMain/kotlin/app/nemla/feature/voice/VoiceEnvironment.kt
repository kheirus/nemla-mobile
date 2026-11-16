package app.nemla.feature.voice

import androidx.compose.ui.text.intl.Locale
import app.nemla.core.domain.model.AppLanguage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

internal fun interface DictationLanguage {
    fun current(): AppLanguage
}

internal object SystemDictationLanguage : DictationLanguage {
    override fun current(): AppLanguage = AppLanguage.fromTag(Locale.current.language.lowercase())
}

internal fun interface Today {
    fun date(): LocalDate
}

internal object SystemToday : Today {
    override fun date(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
}
