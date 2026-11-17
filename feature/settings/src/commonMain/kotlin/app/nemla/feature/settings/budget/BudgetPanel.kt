package app.nemla.feature.settings.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaOption
import app.nemla.core.designsystem.component.NemlaSegmentedButtons
import app.nemla.core.designsystem.component.NemlaTextField
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.feature.settings.common.Caption
import app.nemla.feature.settings.common.NoticeBanner
import app.nemla.feature.settings.common.PanelCard
import app.nemla.feature.settings.common.money
import app.nemla.feature.settings.common.tabular
import app.nemla.feature.settings.resources.Res
import app.nemla.feature.settings.resources.budget_amount_label
import app.nemla.feature.settings.resources.budget_by_category
import app.nemla.feature.settings.resources.budget_estimate
import app.nemla.feature.settings.resources.budget_estimate_failed
import app.nemla.feature.settings.resources.budget_estimate_help
import app.nemla.feature.settings.resources.budget_estimated
import app.nemla.feature.settings.resources.budget_estimated_placeholder
import app.nemla.feature.settings.resources.budget_fixed
import app.nemla.feature.settings.resources.budget_intro
import app.nemla.feature.settings.resources.budget_monthly
import app.nemla.feature.settings.resources.budget_no_budget
import app.nemla.feature.settings.resources.budget_no_data
import app.nemla.feature.settings.resources.budget_no_history
import app.nemla.feature.settings.resources.budget_per_month
import app.nemla.feature.settings.resources.budget_per_year
import app.nemla.feature.settings.resources.budget_remaining
import app.nemla.feature.settings.resources.budget_save
import app.nemla.feature.settings.resources.budget_save_failed
import app.nemla.feature.settings.resources.budget_saved
import app.nemla.feature.settings.resources.budget_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun BudgetPanelRoute(
    onOpenCategories: () -> Unit,
    viewModel: BudgetViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onAction(BudgetAction.Opened) }
    BudgetPanel(state = state, onAction = viewModel::onAction, onOpenCategories = onOpenCategories)
}

@Composable
internal fun BudgetPanel(
    state: BudgetUiState,
    onAction: (BudgetAction) -> Unit,
    onOpenCategories: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md)) {
        state.notice?.let { notice ->
            NoticeBanner(text = notice.message.text(state), kind = notice.kind, onDismiss = { onAction(BudgetAction.NoticeDismissed) })
        }
        PanelCard(title = stringResource(Res.string.budget_title), subtitle = stringResource(Res.string.budget_intro)) {
            val estimate = state.estimate?.takeIf { state.hasHistory && it.monthlyCents > 0 }
            NemlaTextField(
                value = state.amountText,
                onValueChange = { onAction(BudgetAction.AmountChanged(it)) },
                label = stringResource(Res.string.budget_amount_label, state.currency.symbol),
                placeholder =
                    if (estimate != null) {
                        stringResource(Res.string.budget_estimated_placeholder, money(estimate.monthlyCents, state.currency))
                    } else {
                        stringResource(Res.string.budget_no_budget)
                    },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            NemlaSegmentedButtons(
                options =
                    listOf(
                        NemlaOption(BudgetPeriod.MONTH, stringResource(Res.string.budget_per_month)),
                        NemlaOption(BudgetPeriod.YEAR, stringResource(Res.string.budget_per_year)),
                    ),
                selected = state.period,
                onSelect = { onAction(BudgetAction.PeriodSelected(it)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Summary(state)
            NemlaButton(
                text = stringResource(Res.string.budget_save),
                onClick = { onAction(BudgetAction.SaveClicked) },
                enabled = state.canSave,
                loading = state.isSaving,
                leadingIcon = NemlaIcons.Check,
                modifier = Modifier.fillMaxWidth(),
            )
            NemlaButton(
                text = stringResource(Res.string.budget_estimate),
                onClick = { onAction(BudgetAction.EstimateClicked) },
                variant = NemlaButtonVariant.Secondary,
                enabled = state.hasHistory,
                loading = state.isEstimating,
                leadingIcon = NemlaIcons.Wand,
                modifier = Modifier.fillMaxWidth(),
            )
            Caption(stringResource(if (state.hasHistory) Res.string.budget_estimate_help else Res.string.budget_no_data))
            NemlaButton(
                text = stringResource(Res.string.budget_by_category),
                onClick = onOpenCategories,
                variant = NemlaButtonVariant.Outline,
                leadingIcon = NemlaIcons.ListTree,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Summary(state: BudgetUiState) {
    val colors = NemlaTheme.colors
    val shape = RoundedCornerShape(NemlaTheme.radius.xl)
    val dash = "—"
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.muted.copy(alpha = 0.4f), shape)
                .border(1.dp, colors.border, shape)
                .padding(NemlaTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
    ) {
        SummaryCell(stringResource(Res.string.budget_monthly), state.monthlyCents?.let { money(it, state.currency) } ?: dash, false)
        SummaryCell(stringResource(Res.string.budget_fixed), money(state.fixedMonthlyCents, state.currency), false)
        val remaining = state.remainingCents
        SummaryCell(stringResource(Res.string.budget_remaining), remaining?.let { money(it, state.currency) } ?: dash, (remaining ?: 0) < 0)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SummaryCell(
    label: String,
    value: String,
    negative: Boolean,
) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = NemlaTheme.typography.caption.copy(fontSize = 11.sp),
            color = NemlaTheme.colors.mutedForeground,
            textAlign = TextAlign.Center,
        )
        Text(
            value,
            style =
                NemlaTheme.typography.body
                    .copy(fontWeight = FontWeight.SemiBold)
                    .tabular(),
            color = if (negative) NemlaTheme.colors.destructive else NemlaTheme.colors.foreground,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BudgetMessage.text(state: BudgetUiState): String =
    when (this) {
        BudgetMessage.Saved -> stringResource(Res.string.budget_saved)
        BudgetMessage.SaveFailed -> stringResource(Res.string.budget_save_failed)
        is BudgetMessage.Estimated -> stringResource(Res.string.budget_estimated, money(monthlyCents, state.currency))
        BudgetMessage.NoHistory -> stringResource(Res.string.budget_no_history)
        BudgetMessage.EstimateFailed -> stringResource(Res.string.budget_estimate_failed)
    }
