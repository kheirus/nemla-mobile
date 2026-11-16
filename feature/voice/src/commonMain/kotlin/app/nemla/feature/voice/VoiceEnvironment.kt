package app.nemla.feature.voice

import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.usecase.CurrentLanguages
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

internal fun interface DictationLanguage {
    fun current(): AppLanguage
}

internal class PreferredDictationLanguage(
    private val languages: CurrentLanguages,
) : DictationLanguage {
    override fun current(): AppLanguage = languages.dictation()
}

internal fun interface Today {
    fun date(): LocalDate
}

internal object SystemToday : Today {
    override fun date(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
}
