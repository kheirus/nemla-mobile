package app.nemla.feature.voice.common

import androidx.compose.runtime.Composable
import app.nemla.core.designsystem.component.NemlaAlertDialog
import app.nemla.core.designsystem.icon.NemlaIcons
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
    NemlaAlertDialog(
        title = stringResource(Res.string.voice_consent_title),
        text = stringResource(Res.string.voice_consent_body),
        confirmLabel = stringResource(Res.string.voice_consent_accept),
        dismissLabel = stringResource(Res.string.voice_consent_decline),
        onConfirm = onAccept,
        onDismissRequest = onDecline,
        icon = NemlaIcons.ShieldCheck,
    )
}
