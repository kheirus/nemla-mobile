package app.nemla.feature.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.nemla.core.designsystem.component.NemlaIconButton
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.InboxChannel
import app.nemla.core.domain.model.InboxItem
import app.nemla.core.domain.model.InboxItemId
import app.nemla.core.domain.model.InboxKind
import app.nemla.feature.reports.resources.Res
import app.nemla.feature.reports.resources.reports_inbox_dismiss
import app.nemla.feature.reports.resources.reports_notice_view
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun InboxBanners() {
    InboxBannersRoute()
}

@Composable
internal fun InboxBannersRoute(viewModel: InboxBannersViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onAction(InboxBannersAction.Started)
            awaitCancellation()
        }
    }
    InboxBannersContent(state = state, onAction = viewModel::onAction)
    state.opened?.let { opened ->
        DeliveredReportDialog(opened = opened, onDismiss = { viewModel.onAction(InboxBannersAction.DialogClosed) })
    }
}

@Composable
internal fun InboxBannersContent(
    state: InboxBannersUiState,
    onAction: (InboxBannersAction) -> Unit,
) {
    if (state.items.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm)) {
        state.items.forEach { item ->
            InboxCard(
                item = item,
                onClick = { onAction(InboxBannersAction.ItemClicked(item.id)) },
                onDismiss = { onAction(InboxBannersAction.DismissClicked(item.id)) },
            )
        }
    }
}

@Composable
private fun InboxCard(
    item: InboxItem,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NemlaTheme.colors
    val reminder = item.kind == InboxKind.DAILY_REMINDER
    Surface(
        shape = RoundedCornerShape(NemlaTheme.radius.xl),
        color = colors.card,
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(NemlaTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(BadgeSize).background(colors.accent.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (reminder) NemlaIcons.ListChecks else NemlaIcons.BellRing,
                    contentDescription = null,
                    modifier = Modifier.size(NemlaTheme.sizes.icon),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = NemlaTheme.typography.body.copy(fontWeight = FontWeight.SemiBold))
                Text(item.body, style = NemlaTheme.typography.caption, color = colors.mutedForeground)
            }
            if (!reminder) {
                Text(
                    stringResource(Res.string.reports_notice_view),
                    style = NemlaTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                    color = colors.primary,
                )
            }
            NemlaIconButton(
                icon = NemlaIcons.X,
                contentDescription = stringResource(Res.string.reports_inbox_dismiss),
                onClick = onDismiss,
                tint = colors.mutedForeground,
                size = DismissSize,
            )
        }
    }
}

private val BadgeSize = 36.dp
private val DismissSize = 32.dp

@Preview
@Composable
private fun InboxBannersPreview() {
    NemlaTheme {
        InboxBannersContent(
            state =
                InboxBannersUiState(
                    language = AppLanguage.FR,
                    items =
                        listOf(
                            InboxItem(
                                InboxItemId("n-1"),
                                InboxChannel.IN_APP,
                                InboxKind.DAILY_REMINDER,
                                "Bilan de la journée",
                                "Pensez à dicter vos dépenses du jour.",
                                null,
                                null,
                                false,
                            ),
                            InboxItem(
                                InboxItemId("n-2"),
                                InboxChannel.IN_APP,
                                InboxKind.MONTH_REPORT,
                                "Votre bilan d'octobre",
                                "Où est parti votre argent ce mois-ci.",
                                null,
                                null,
                                false,
                            ),
                        ),
                ),
            onAction = {},
        )
    }
}
