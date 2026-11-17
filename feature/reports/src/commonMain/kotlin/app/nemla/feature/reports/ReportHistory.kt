package app.nemla.feature.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaCard
import app.nemla.core.designsystem.component.NemlaLoadingIndicator
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.ReportYearGroup
import app.nemla.core.domain.model.ReviewPeriod
import app.nemla.feature.reports.resources.Res
import app.nemla.feature.reports.resources.reports_history_annual
import app.nemla.feature.reports.resources.reports_history_body
import app.nemla.feature.reports.resources.reports_history_empty
import app.nemla.feature.reports.resources.reports_history_failed
import app.nemla.feature.reports.resources.reports_history_loading
import app.nemla.feature.reports.resources.reports_history_title
import app.nemla.feature.reports.resources.reports_retry
import kotlinx.coroutines.awaitCancellation
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReportHistory() {
    ReportHistoryRoute()
}

@Composable
internal fun ReportHistoryRoute(viewModel: ReportHistoryViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onAction(ReportHistoryAction.Started)
            awaitCancellation()
        }
    }
    ReportHistoryContent(state = state, onAction = viewModel::onAction)
    state.opened?.let { period ->
        ReportDialog(period = period, onDismiss = { viewModel.onAction(ReportHistoryAction.DialogClosed) })
    }
}

@Composable
internal fun ReportHistoryContent(
    state: ReportHistoryUiState,
    onAction: (ReportHistoryAction) -> Unit,
) {
    val colors = NemlaTheme.colors
    NemlaCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
                    Icon(
                        NemlaIcons.History,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(NemlaTheme.sizes.icon),
                    )
                    Text(
                        stringResource(Res.string.reports_history_title),
                        style = NemlaTheme.typography.cardTitle,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Text(stringResource(Res.string.reports_history_body), style = NemlaTheme.typography.caption, color = colors.mutedForeground)
            }
            when (val content = state.content) {
                HistoryContent.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
                    ) {
                        NemlaLoadingIndicator(modifier = Modifier.size(NemlaTheme.sizes.iconLarge))
                        Text(
                            stringResource(Res.string.reports_history_loading),
                            style = NemlaTheme.typography.body,
                            color = colors.mutedForeground,
                        )
                    }
                }

                HistoryContent.Failed -> {
                    Text(
                        stringResource(Res.string.reports_history_failed),
                        style = NemlaTheme.typography.body,
                        color = colors.mutedForeground,
                    )
                    NemlaButton(
                        text = stringResource(Res.string.reports_retry),
                        onClick = { onAction(ReportHistoryAction.RetryClicked) },
                        variant = NemlaButtonVariant.Outline,
                        leadingIcon = NemlaIcons.RefreshCw,
                    )
                }

                is HistoryContent.Loaded -> {
                    if (content.groups.isEmpty()) {
                        Text(
                            stringResource(Res.string.reports_history_empty),
                            style = NemlaTheme.typography.body,
                            color = colors.mutedForeground,
                        )
                    } else {
                        content.groups.forEach { group -> YearGroup(group, state.language, onAction) }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearGroup(
    group: ReportYearGroup,
    language: AppLanguage,
    onAction: (ReportHistoryAction) -> Unit,
) {
    val colors = NemlaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
        Text(
            group.year.toString(),
            style = NemlaTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.mutedForeground,
            modifier = Modifier.semantics { heading() },
        )
        Surface(
            shape = RoundedCornerShape(NemlaTheme.radius.xl),
            color = colors.card,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                group.periods.forEachIndexed { index, period ->
                    if (index > 0) HorizontalDivider(color = colors.border)
                    PeriodRow(period, language) { onAction(ReportHistoryAction.PeriodClicked(period)) }
                }
            }
        }
    }
}

@Composable
private fun PeriodRow(
    period: ReviewPeriod,
    language: AppLanguage,
    onClick: () -> Unit,
) {
    val colors = NemlaTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = NemlaTheme.sizes.minTouchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = NemlaTheme.spacing.sm, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
    ) {
        Text(periodLabel(period, language), style = NemlaTheme.typography.bodyStrong, modifier = Modifier.weight(1f, fill = false))
        if (period is ReviewPeriod.Year) {
            Text(
                stringResource(Res.string.reports_history_annual).uppercase(),
                style = NemlaTheme.typography.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                modifier =
                    Modifier
                        .background(colors.accent.copy(alpha = 0.2f), RoundedCornerShape(NemlaTheme.radius.sm))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            Icon(
                NemlaIcons.ChevronRight,
                contentDescription = null,
                tint = colors.mutedForeground,
                modifier = Modifier.size(NemlaTheme.sizes.icon),
            )
        }
    }
}

@Preview
@Composable
private fun ReportHistoryPreview() {
    NemlaTheme {
        ReportHistoryContent(
            state =
                ReportHistoryUiState(
                    language = AppLanguage.FR,
                    content =
                        HistoryContent.Loaded(
                            listOf(
                                ReportYearGroup(2026, listOf(ReviewPeriod.Month(YearMonth(2026, 10)))),
                                ReportYearGroup(2025, listOf(ReviewPeriod.Year(2025), ReviewPeriod.Month(YearMonth(2025, 12)))),
                            ),
                        ),
                ),
            onAction = {},
        )
    }
}
