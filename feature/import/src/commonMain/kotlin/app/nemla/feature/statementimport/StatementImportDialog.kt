package app.nemla.feature.statementimport

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.nemla.core.designsystem.component.NemlaAlertDialog
import app.nemla.core.designsystem.component.NemlaBanner
import app.nemla.core.designsystem.component.NemlaBannerKind
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaFormSheet
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.documents.rememberDocumentPicker
import app.nemla.core.domain.model.PendingReview
import app.nemla.feature.statementimport.resources.Res
import app.nemla.feature.statementimport.resources.import_choose_file
import app.nemla.feature.statementimport.resources.import_consent_accept
import app.nemla.feature.statementimport.resources.import_consent_body
import app.nemla.feature.statementimport.resources.import_consent_decline
import app.nemla.feature.statementimport.resources.import_consent_title
import app.nemla.feature.statementimport.resources.import_description
import app.nemla.feature.statementimport.resources.import_no_file
import app.nemla.feature.statementimport.resources.import_parsing
import app.nemla.feature.statementimport.resources.import_quota_exhausted
import app.nemla.feature.statementimport.resources.import_quota_left
import app.nemla.feature.statementimport.resources.import_read
import app.nemla.feature.statementimport.resources.import_reading
import app.nemla.feature.statementimport.resources.import_redactions
import app.nemla.feature.statementimport.resources.import_title
import app.nemla.feature.statementimport.resources.import_trust_body
import app.nemla.feature.statementimport.resources.import_trust_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StatementImportDialog(
    onClose: () -> Unit,
    onImported: (PendingReview) -> Unit,
) {
    StatementImportRoute(onClose = onClose, onImported = onImported)
}

@Composable
internal fun StatementImportRoute(
    onClose: () -> Unit,
    onImported: (PendingReview) -> Unit,
    viewModel: StatementImportViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnImported by rememberUpdatedState(onImported)
    val pick = rememberDocumentPicker { document -> viewModel.onAction(StatementImportAction.FilePicked(document)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel) { viewModel.onAction(StatementImportAction.Opened) }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is StatementImportEffect.Imported -> currentOnImported(effect.review)
                    StatementImportEffect.Close -> currentOnClose()
                }
            }
        }
    }
    StatementImportContent(state = state, onAction = viewModel::onAction, onPick = pick)
}

@Composable
internal fun StatementImportContent(
    state: StatementImportUiState,
    onAction: (StatementImportAction) -> Unit,
    onPick: () -> Unit,
) {
    NemlaFormSheet(
        title = stringResource(Res.string.import_title),
        description = stringResource(Res.string.import_description),
        dismissible = state.isDismissible,
        onDismissRequest = { onAction(StatementImportAction.Dismissed) },
    ) {
        TrustNotice()
        QuotaLine(state.quota)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
        ) {
            NemlaButton(
                text = stringResource(Res.string.import_choose_file),
                onClick = onPick,
                variant = NemlaButtonVariant.Secondary,
                enabled = state.canPick,
                leadingIcon = NemlaIcons.FileUp,
            )
            Text(
                text = state.fileName ?: stringResource(Res.string.import_no_file),
                style = NemlaTheme.typography.caption,
                color = NemlaTheme.colors.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        state.redactions?.let { count ->
            Text(
                text = pluralStringResource(Res.plurals.import_redactions, count, count),
                style = NemlaTheme.typography.caption,
                color = NemlaTheme.colors.mutedForeground,
            )
        }
        state.message?.let { message ->
            NemlaBanner(
                message = message.text(),
                kind = if (message.isInformation) NemlaBannerKind.Info else NemlaBannerKind.Error,
            )
        }
        NemlaButton(
            text =
                stringResource(
                    when (state.phase) {
                        ImportPhase.Idle -> Res.string.import_read
                        ImportPhase.Reading -> Res.string.import_reading
                        ImportPhase.Parsing -> Res.string.import_parsing
                    },
                ),
            onClick = { onAction(StatementImportAction.ReadClicked) },
            enabled = state.canRead,
            loading = state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (state.isConsentPromptVisible) {
        ImportConsentDialog(
            onAccept = { onAction(StatementImportAction.ConsentAccepted) },
            onDecline = { onAction(StatementImportAction.ConsentDeclined) },
        )
    }
}

@Composable
private fun TrustNotice() {
    val colors = NemlaTheme.colors
    Surface(
        shape = RoundedCornerShape(NemlaTheme.radius.xl),
        color = colors.primary.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(NemlaTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
        ) {
            Icon(
                NemlaIcons.ShieldCheck,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(NemlaTheme.sizes.iconLarge),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.import_trust_title), style = NemlaTheme.typography.bodyStrong, color = colors.foreground)
                Text(stringResource(Res.string.import_trust_body), style = NemlaTheme.typography.caption, color = colors.foreground)
            }
        }
    }
}

@Composable
private fun QuotaLine(quota: ImportQuota) {
    val colors = NemlaTheme.colors
    val caption = NemlaTheme.typography.caption
    when (quota) {
        is ImportQuota.Available -> {
            Text(
                text = pluralStringResource(Res.plurals.import_quota_left, quota.remaining, quota.remaining),
                style = caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.creditOk,
            )
        }

        ImportQuota.Exhausted -> {
            Text(text = stringResource(Res.string.import_quota_exhausted), style = caption, color = colors.destructive)
        }

        ImportQuota.Loading, ImportQuota.Unknown -> {
            Unit
        }
    }
}

@Composable
private fun ImportConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    NemlaAlertDialog(
        title = stringResource(Res.string.import_consent_title),
        text = stringResource(Res.string.import_consent_body),
        confirmLabel = stringResource(Res.string.import_consent_accept),
        dismissLabel = stringResource(Res.string.import_consent_decline),
        onConfirm = onAccept,
        onDismissRequest = onDecline,
        icon = NemlaIcons.ShieldCheck,
    )
}

@Preview
@Composable
private fun StatementImportIdlePreview() {
    NemlaTheme {
        StatementImportContent(
            state = StatementImportUiState(quota = ImportQuota.Available(1), fileName = "releve-mars.csv"),
            onAction = {},
            onPick = {},
        )
    }
}

@Preview
@Composable
private fun StatementImportNotSupportedPreview() {
    NemlaTheme {
        StatementImportContent(
            state =
                StatementImportUiState(
                    quota = ImportQuota.Available(1),
                    fileName = "releve-mars.pdf",
                    redactions = 7,
                    message = ImportMessage.NOT_SUPPORTED_YET,
                ),
            onAction = {},
            onPick = {},
        )
    }
}
