package app.nemla.feature.settings.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import app.nemla.core.common.AppError
import app.nemla.core.designsystem.component.NemlaBanner
import app.nemla.core.designsystem.component.NemlaBannerKind
import app.nemla.core.designsystem.component.NemlaCard
import app.nemla.core.designsystem.component.NemlaSnackbarEffect
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.format.MoneyFormat
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.Currency
import app.nemla.feature.settings.resources.Res
import app.nemla.feature.settings.resources.settings_dismiss
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

internal enum class NoticeKind { SUCCESS, INFO, ERROR }

internal data class Notice<M>(
    val kind: NoticeKind,
    val message: M,
)

internal fun <M> success(message: M) = Notice(NoticeKind.SUCCESS, message)

internal fun <M> info(message: M) = Notice(NoticeKind.INFO, message)

internal fun <M> error(message: M) = Notice(NoticeKind.ERROR, message)

internal fun AppError.isOffline(): Boolean = this == AppError.Network.Offline || this == AppError.Network.Timeout

internal fun money(
    cents: Long,
    currency: Currency,
): String = MoneyFormat.format(cents, currency)

internal fun decimalSeparator(language: AppLanguage): Char = if (language == AppLanguage.EN) '.' else ','

private const val NOTICE_DURATION_MS = 4_000L

@Composable
internal fun NoticeBanner(
    text: String,
    kind: NoticeKind,
    onDismiss: () -> Unit,
) {
    if (kind != NoticeKind.ERROR) {
        NemlaSnackbarEffect(message = text, onShown = onDismiss)
        return
    }
    LaunchedEffect(text, kind) {
        delay(NOTICE_DURATION_MS)
        onDismiss()
    }
    val label = stringResource(Res.string.settings_dismiss)
    NemlaBanner(
        message = text,
        kind = NemlaBannerKind.Error,
        modifier = Modifier.clickable(onClickLabel = label, onClick = onDismiss),
    )
}

@Composable
internal fun PanelCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    NemlaCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs)) {
                Text(title, style = NemlaTheme.typography.cardTitle, modifier = Modifier.semantics { heading() })
                if (subtitle != null) Text(subtitle, style = NemlaTheme.typography.body, color = NemlaTheme.colors.mutedForeground)
            }
            content()
        }
    }
}

@Composable
internal fun Caption(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = NemlaTheme.typography.caption,
) {
    Text(text, style = style, color = NemlaTheme.colors.mutedForeground, modifier = modifier)
}

internal fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
