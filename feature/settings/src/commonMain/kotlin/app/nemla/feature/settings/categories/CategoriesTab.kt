package app.nemla.feature.settings.categories

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaCategoryDot
import app.nemla.core.designsystem.component.NemlaChip
import app.nemla.core.designsystem.component.NemlaIconButton
import app.nemla.core.designsystem.component.NemlaOption
import app.nemla.core.designsystem.component.NemlaSelectField
import app.nemla.core.designsystem.component.NemlaSwitch
import app.nemla.core.designsystem.component.NemlaTextField
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.CategoryColors
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.feature.settings.common.Caption
import app.nemla.feature.settings.common.NoticeBanner
import app.nemla.feature.settings.common.PanelCard
import app.nemla.feature.settings.common.money
import app.nemla.feature.settings.resources.Res
import app.nemla.feature.settings.resources.categories_add
import app.nemla.feature.settings.resources.categories_added
import app.nemla.feature.settings.resources.categories_budget
import app.nemla.feature.settings.resources.categories_delete_named
import app.nemla.feature.settings.resources.categories_deleted
import app.nemla.feature.settings.resources.categories_estimate
import app.nemla.feature.settings.resources.categories_estimated_placeholder
import app.nemla.feature.settings.resources.categories_failed
import app.nemla.feature.settings.resources.categories_fixed
import app.nemla.feature.settings.resources.categories_intro
import app.nemla.feature.settings.resources.categories_new
import app.nemla.feature.settings.resources.categories_no_budget
import app.nemla.feature.settings.resources.categories_no_data
import app.nemla.feature.settings.resources.categories_per_month
import app.nemla.feature.settings.resources.categories_per_year
import app.nemla.feature.settings.resources.categories_period
import app.nemla.feature.settings.resources.categories_rename
import app.nemla.feature.settings.resources.categories_show_named
import app.nemla.feature.settings.resources.categories_title
import app.nemla.feature.settings.resources.categories_updated
import app.nemla.feature.settings.resources.categories_visible
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun CategoriesTabRoute(viewModel: CategoriesViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onAction(CategoriesAction.Opened) }
    CategoriesTab(state = state, onAction = viewModel::onAction)
}

@Composable
internal fun CategoriesTab(
    state: CategoriesUiState,
    onAction: (CategoriesAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md)) {
        state.notice?.let { notice ->
            NoticeBanner(text = notice.message.text(), kind = notice.kind, onDismiss = { onAction(CategoriesAction.NoticeDismissed) })
        }
        PanelCard(
            title = stringResource(Res.string.categories_title),
            subtitle = pluralStringResource(Res.plurals.categories_intro, state.rows.size, state.rows.size),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                NemlaTextField(
                    value = state.newName,
                    onValueChange = { onAction(CategoriesAction.NewNameChanged(it)) },
                    label = stringResource(Res.string.categories_new),
                    labelVisible = false,
                    placeholder = stringResource(Res.string.categories_new),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAction(CategoriesAction.AddClicked) }),
                    modifier = Modifier.weight(1f),
                )
                NemlaIconButton(
                    icon = NemlaIcons.Plus,
                    contentDescription = stringResource(Res.string.categories_add),
                    onClick = { onAction(CategoriesAction.AddClicked) },
                    enabled = state.newName.isNotBlank() && !state.isAdding,
                    bordered = true,
                )
            }
            state.rows.forEach { row -> CategoryTile(row, state, onAction) }
        }
    }
}

@Composable
private fun CategoryTile(
    row: CategoryRow,
    state: CategoriesUiState,
    onAction: (CategoriesAction) -> Unit,
) {
    val shape = RoundedCornerShape(NemlaTheme.radius.xl)
    val name = row.category.name
    Column(
        modifier = Modifier.fillMaxWidth().border(1.dp, NemlaTheme.colors.border, shape).padding(NemlaTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            NemlaCategoryDot(CategoryColors.parse(row.category.colorHex), Modifier.size(16.dp))
            Text(
                name,
                style = NemlaTheme.typography.bodyStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Caption(stringResource(Res.string.categories_visible))
            NemlaSwitch(
                checked = row.category.visible,
                onCheckedChange = { onAction(CategoriesAction.VisibilityToggled(row.id, it)) },
                contentDescription = stringResource(Res.string.categories_show_named, name),
            )
            if (!row.category.isDefault) {
                NemlaIconButton(
                    icon = NemlaIcons.Trash2,
                    contentDescription = stringResource(Res.string.categories_delete_named, name),
                    onClick = { onAction(CategoriesAction.DeleteClicked(row.id)) },
                    tint = NemlaTheme.colors.destructive,
                    size = 40.dp,
                )
            }
        }
        val suggestions = row.suggestions
        if (suggestions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Caption(stringResource(Res.string.categories_rename))
                suggestions.forEach { suggestion ->
                    NemlaChip(text = suggestion, onClick = { onAction(CategoriesAction.RenameClicked(row.id, suggestion)) })
                }
            }
        }
        if (!row.budgetable) {
            Caption(stringResource(Res.string.categories_fixed))
        } else {
            BudgetLine(row, state, onAction)
        }
    }
}

@Composable
private fun BudgetLine(
    row: CategoryRow,
    state: CategoriesUiState,
    onAction: (CategoriesAction) -> Unit,
) {
    val name = row.category.name
    Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs), verticalAlignment = Alignment.Bottom) {
        Column(modifier = Modifier.weight(1f)) {
            NemlaTextField(
                value = row.budgetText,
                onValueChange = { onAction(CategoriesAction.BudgetChanged(row.id, it)) },
                label = stringResource(Res.string.categories_budget, state.currency.symbol),
                placeholder =
                    row.estimateCents?.let { stringResource(Res.string.categories_estimated_placeholder, money(it, state.currency)) }
                        ?: stringResource(Res.string.categories_no_budget),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAction(CategoriesAction.BudgetCommitted(row.id)) }),
                modifier =
                    Modifier.onFocusChanged { focus ->
                        if (!focus.isFocused) onAction(CategoriesAction.BudgetCommitted(row.id))
                    },
            )
        }
        NemlaSelectField(
            label = stringResource(Res.string.categories_period),
            placeholder = stringResource(Res.string.categories_period),
            options =
                listOf(
                    NemlaOption(BudgetPeriod.MONTH, stringResource(Res.string.categories_per_month)),
                    NemlaOption(BudgetPeriod.YEAR, stringResource(Res.string.categories_per_year)),
                ),
            selected = row.period,
            onSelect = { onAction(CategoriesAction.PeriodSelected(row.id, it)) },
            modifier = Modifier.width(112.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        NemlaButton(
            text = stringResource(Res.string.categories_estimate),
            onClick = { onAction(CategoriesAction.EstimateClicked(row.id)) },
            variant = NemlaButtonVariant.Secondary,
            enabled = row.estimateCents != null,
            leadingIcon = NemlaIcons.Wand,
        )
        if (row.estimateCents == null) Caption(stringResource(Res.string.categories_no_data), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CategoriesMessage.text(): String =
    stringResource(
        when (this) {
            CategoriesMessage.ADDED -> Res.string.categories_added
            CategoriesMessage.UPDATED -> Res.string.categories_updated
            CategoriesMessage.DELETED -> Res.string.categories_deleted
            CategoriesMessage.FAILED -> Res.string.categories_failed
        },
    )
