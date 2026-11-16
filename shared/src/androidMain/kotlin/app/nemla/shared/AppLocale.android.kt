package app.nemla.shared

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import app.nemla.core.domain.model.AppLanguage
import java.util.Locale

private val systemLocale: Locale by lazy { Locale.getDefault() }

@Composable
internal actual fun ProvideAppLanguage(
    language: AppLanguage?,
    content: @Composable () -> Unit,
) {
    val base = LocalConfiguration.current
    val target = language?.let { Locale.forLanguageTag(it.tag) } ?: systemLocale
    if (Locale.getDefault() != target) Locale.setDefault(target)
    val configuration = remember(base, target) { Configuration(base).apply { setLocale(target) } }
    CompositionLocalProvider(LocalConfiguration provides configuration) {
        key(target.language) { content() }
    }
}
