package app.nemla.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import app.nemla.core.domain.model.AppLanguage
import platform.Foundation.NSUserDefaults

private const val APPLE_LANGUAGES = "AppleLanguages"

@Composable
internal actual fun ProvideAppLanguage(
    language: AppLanguage?,
    content: @Composable () -> Unit,
) {
    val tag = language?.tag
    remember(tag) {
        val defaults = NSUserDefaults.standardUserDefaults
        if (tag == null) defaults.removeObjectForKey(APPLE_LANGUAGES) else defaults.setObject(listOf(tag), forKey = APPLE_LANGUAGES)
        tag
    }
    key(tag) { content() }
}
