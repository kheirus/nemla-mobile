package app.nemla.core.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppPreferences(
    val appLanguage: AppLanguage? = null,
    val dictationLanguage: AppLanguage? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
) {
    companion object {
        val Default: AppPreferences = AppPreferences()
    }
}

enum class StartDestination { ONBOARDING, DASHBOARD }
