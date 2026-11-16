package app.nemla.feature.voice.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.feature.voice.resources.Res
import app.nemla.feature.voice.resources.voice_consent_accept
import app.nemla.feature.voice.resources.voice_consent_body
import app.nemla.feature.voice.resources.voice_consent_decline
import app.nemla.feature.voice.resources.voice_consent_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AiConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val colors = NemlaTheme.colors
    AlertDialog(
        onDismissRequest = onDecline,
        containerColor = colors.background,
        titleContentColor = colors.foreground,
        textContentColor = colors.mutedForeground,
        icon = { Icon(NemlaIcons.ShieldCheck, contentDescription = null, tint = colors.primary) },
        title = { Text(stringResource(Res.string.voice_consent_title), style = NemlaTheme.typography.cardTitle) },
        text = { Text(stringResource(Res.string.voice_consent_body), style = NemlaTheme.typography.body) },
        confirmButton = { NemlaButton(text = stringResource(Res.string.voice_consent_accept), onClick = onAccept) },
        dismissButton = {
            NemlaButton(text = stringResource(Res.string.voice_consent_decline), onClick = onDecline, variant = NemlaButtonVariant.Ghost)
        },
    )
}
