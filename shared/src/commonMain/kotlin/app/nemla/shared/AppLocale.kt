package app.nemla.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.port.DeviceLanguage

internal object ComposeDeviceLanguage : DeviceLanguage {
    private val initial: AppLanguage by lazy { AppLanguage.fromTag(Locale.current.language.lowercase()) }

    override fun current(): AppLanguage = initial
}

@Composable
internal expect fun ProvideAppLanguage(
    language: AppLanguage?,
    content: @Composable () -> Unit,
)
