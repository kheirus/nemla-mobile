package app.nemla.core.security

interface ScreenProtection {
    val isEnabled: Boolean

    fun setEnabled(enabled: Boolean)
}
