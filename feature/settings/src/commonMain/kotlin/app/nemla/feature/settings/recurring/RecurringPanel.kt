package app.nemla.feature.settings.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaCategoryDot
import app.nemla.core.designsystem.component.NemlaIconButton
import app.nemla.core.designsystem.component.NemlaOption
import app.nemla.core.designsystem.component.NemlaSelectField
import app.nemla.core.designsystem.component.NemlaTextArea
import app.nemla.core.designsystem.component.NemlaTextField
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.CategoryColors
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.ChargeFrequency
import app.nemla.core.domain.model.RecurringCharge
import app.nemla.feature.settings.SettingsDictationSlot
import app.nemla.feature.settings.common.Caption
import app.nemla.feature.settings.common.NoticeBanner
import app.nemla.feature.settings.common.PanelCard
import app.nemla.feature.settings.common.money
import app.nemla.feature.settings.common.tabular
import app.nemla.feature.settings.resources.Res
import app.nemla.feature.settings.resources.recurring_add_help
import app.nemla.feature.settings.resources.recurring_add_title
import app.nemla.feature.settings.resources.recurring_amount
import app.nemla.feature.settings.resources.recurring_analysis_failed
import app.nemla.feature.settings.resources.recurring_analyze
import app.nemla.feature.settings.resources.recurring_category
import app.nemla.feature.settings.resources.recurring_day
import app.nemla.feature.settings.resources.recurring_day_label
import app.nemla.feature.settings.resources.recurring_delete
import app.nemla.feature.settings.resources.recurring_delete_failed
import app.nemla.feature.settings.resources.recurring_deleted
import app.nemla.feature.settings.resources.recurring_empty
import app.nemla.feature.settings.resources.recurring_frequency
import app.nemla.feature.settings.resources.recurring_input_label
import app.nemla.feature.settings.resources.recurring_input_placeholder
import app.nemla.feature.settings.resources.recurring_intro
import app.nemla.feature.settings.resources.recurring_list_title
import app.nemla.feature.settings.resources.recurring_manual
import app.nemla.feature.settings.resources.recurring_monthly
import app.nemla.feature.settings.resources.recurring_name
import app.nemla.feature.settings.resources.recurring_new_line
import app.nemla.feature.settings.resources.recurring_next
import app.nemla.feature.settings.resources.recurring_nothing_detected
import app.nemla.feature.settings.resources.recurring_page
import app.nemla.feature.settings.resources.recurring_previous
import app.nemla.feature.settings.resources.recurring_remove
import app.nemla.feature.settings.resources.recurring_review_title
import app.nemla.feature.settings.resources.recurring_row_meta
import app.nemla.feature.settings.resources.recurring_save
import app.nemla.feature.settings.resources.recurring_save_failed
import app.nemla.feature.settings.resources.recurring_saved
import app.nemla.feature.settings.resources.recurring_summary
import app.nemla.feature.settings.resources.recurring_title
import app.nemla.feature.settings.resources.recurring_total_month
import app.nemla.feature.settings.resources.recurring_total_year
import app.nemla.feature.settings.resources.recurring_uncategorized
import app.nemla.feature.settings.resources.recurring_yearly
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun RecurringPanelRoute(
    dictation: SettingsDictationSlot,
    viewModel: RecurringViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onAction(RecurringAction.Opened) }
    RecurringPanel(state = state, onAction = viewModel::onAction, dictation = dictation)
}

@Composable
internal fun RecurringPanel(
    state: RecurringUiState,
    onAction: (RecurringAction) -> Unit,
    dictation: SettingsDictationSlot,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md)) {
        Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs)) {
            Text(stringResource(Res.string.recurring_title), style = NemlaTheme.typography.sectionTitle)
            Caption(stringResource(Res.string.recurring_intro), style = NemlaTheme.typography.body)
        }
        state.notice?.let { notice ->
            NoticeBanner(text = notice.message.text(), kind = notice.kind, onDismiss = { onAction(RecurringAction.NoticeDismissed) })
        }
        AddCard(state, onAction, dictation)
        if (state.drafts.isNotEmpty()) ReviewCard(state, onAction)
        ListCard(state, onAction)
    }
}

@Composable
private fun AddCard(
    state: RecurringUiState,
    onAction: (RecurringAction) -> Unit,
    dictation: SettingsDictationSlot,
) {
    PanelCard(title = stringResource(Res.string.recurring_add_title), subtitle = stringResource(Res.string.recurring_add_help)) {
        NemlaTextArea(
            value = state.text,
            onValueChange = { onAction(RecurringAction.TextChanged(it)) },
            label = stringResource(Res.string.recurring_input_label),
            placeholder = stringResource(Res.string.recurring_input_placeholder),
            minLines = 3,
            maxLength = 4_000,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
        ) {
            NemlaButton(
                text = stringResource(Res.string.recurring_analyze),
                onClick = { onAction(RecurringAction.AnalyzeClicked) },
                enabled = state.canAnalyze,
                loading = state.isAnalyzing,
                leadingIcon = NemlaIcons.Sparkles,
            )
            dictation(
                !state.isAnalyzing && !state.isSaving,
                { onAction(RecurringAction.Transcribed(it)) },
                { onAction(RecurringAction.DictationActiveChanged(it)) },
                { onAction(RecurringAction.DictationMessage(it)) },
            )
            NemlaButton(
                text = stringResource(Res.string.recurring_manual),
                onClick = { onAction(RecurringAction.ManualToggled) },
                variant = NemlaButtonVariant.Ghost,
                leadingIcon = NemlaIcons.Plus,
            )
        }
        if (state.isManualOpen) {
            val shape = RoundedCornerShape(NemlaTheme.radius.lg)
            Column(
                modifier = Modifier.fillMaxWidth().border(1.dp, NemlaTheme.colors.border, shape).padding(NemlaTheme.spacing.sm),
            ) {
                NemlaButton(
                    text = stringResource(Res.string.recurring_new_line),
                    onClick = { onAction(RecurringAction.BlankRowAdded) },
                    variant = NemlaButtonVariant.Outline,
                    leadingIcon = NemlaIcons.Plus,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ReviewCard(
    state: RecurringUiState,
    onAction: (RecurringAction) -> Unit,
) {
    val categoryOptions = state.categories.map { NemlaOption(it.id, it.name) }
    val colors = state.categories.associate { it.id to it.colorHex }
    val frequencyOptions = frequencyOptions()
    PanelCard(title = stringResource(Res.string.recurring_review_title)) {
        state.drafts.forEach { row ->
            val shape = RoundedCornerShape(NemlaTheme.radius.lg)
            Column(
                modifier = Modifier.fillMaxWidth().border(1.dp, NemlaTheme.colors.border, shape).padding(NemlaTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
                    NemlaTextField(
                        value = row.name,
                        onValueChange = { onAction(RecurringAction.RowNameChanged(row.key, it)) },
                        label = stringResource(Res.string.recurring_name),
                        labelVisible = false,
                        placeholder = stringResource(Res.string.recurring_name),
                        modifier = Modifier.weight(1f),
                    )
                    NemlaTextField(
                        value = row.amountText,
                        onValueChange = { onAction(RecurringAction.RowAmountChanged(row.key, it)) },
                        label = stringResource(Res.string.recurring_amount),
                        labelVisible = false,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(104.dp),
                    )
                }
                NemlaSelectField(
                    label = stringResource(Res.string.recurring_category),
                    placeholder = stringResource(Res.string.recurring_category),
                    options = categoryOptions,
                    selected = row.categoryId,
                    onSelect = { onAction(RecurringAction.RowCategorySelected(row.key, it)) },
                    labelVisible = false,
                    leading = { id -> NemlaCategoryDot(CategoryColors.parse(colors[id]), Modifier.size(12.dp)) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    NemlaSelectField(
                        label = stringResource(Res.string.recurring_frequency),
                        placeholder = stringResource(Res.string.recurring_frequency),
                        options = frequencyOptions,
                        selected = row.frequency,
                        onSelect = { onAction(RecurringAction.RowFrequencySelected(row.key, it)) },
                        labelVisible = false,
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(Res.string.recurring_day_label), style = NemlaTheme.typography.body)
                    NemlaTextField(
                        value = row.dayText,
                        onValueChange = { onAction(RecurringAction.RowDayChanged(row.key, it)) },
                        label = stringResource(Res.string.recurring_day),
                        labelVisible = false,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(64.dp),
                    )
                    NemlaIconButton(
                        icon = NemlaIcons.Trash2,
                        contentDescription = stringResource(Res.string.recurring_remove),
                        onClick = { onAction(RecurringAction.RowRemoved(row.key)) },
                    )
                }
            }
        }
        NemlaButton(
            text = stringResource(Res.string.recurring_save),
            onClick = { onAction(RecurringAction.SaveClicked) },
            enabled = state.canSave,
            loading = state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ListCard(
    state: RecurringUiState,
    onAction: (RecurringAction) -> Unit,
) {
    PanelCard(
        title = stringResource(Res.string.recurring_list_title),
        subtitle =
            pluralStringResource(
                Res.plurals.recurring_summary,
                state.charges.size,
                state.charges.size,
                money(state.monthlyTotalCents, state.currency),
            ),
    ) {
        if (state.pageCount > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NemlaIconButton(
                    icon = NemlaIcons.ChevronLeft,
                    contentDescription = stringResource(Res.string.recurring_previous),
                    onClick = { onAction(RecurringAction.PreviousPage) },
                    enabled = state.page > 0,
                    bordered = true,
                    size = 32.dp,
                )
                Caption(stringResource(Res.string.recurring_page, state.page + 1, state.pageCount))
                NemlaIconButton(
                    icon = NemlaIcons.ChevronRight,
                    contentDescription = stringResource(Res.string.recurring_next),
                    onClick = { onAction(RecurringAction.NextPage) },
                    enabled = state.page < state.pageCount - 1,
                    bordered = true,
                    size = 32.dp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
            Total(stringResource(Res.string.recurring_total_month), money(state.monthlyTotalCents, state.currency))
            Total(stringResource(Res.string.recurring_total_year), money(state.yearlyTotalCents, state.currency))
        }
        if (state.charges.isEmpty()) {
            Caption(stringResource(Res.string.recurring_empty), style = NemlaTheme.typography.body)
        } else {
            val categories = state.categories.associateBy { it.id }
            state.visibleCharges.forEach { charge -> ChargeLine(charge, categories[charge.categoryId], state, onAction) }
        }
    }
}

@Composable
private fun RowScope.Total(
    label: String,
    value: String,
) {
    val shape = RoundedCornerShape(NemlaTheme.radius.lg)
    Column(
        modifier =
            Modifier
                .weight(1f)
                .background(NemlaTheme.colors.muted.copy(alpha = 0.4f), shape)
                .padding(NemlaTheme.spacing.sm),
    ) {
        Caption(label)
        Text(value, style = NemlaTheme.typography.bodyStrong.tabular())
    }
}

@Composable
private fun ChargeLine(
    charge: RecurringCharge,
    category: CategoryOption?,
    state: RecurringUiState,
    onAction: (RecurringAction) -> Unit,
) {
    val frequency =
        stringResource(if (charge.frequency == ChargeFrequency.YEARLY) Res.string.recurring_yearly else Res.string.recurring_monthly)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NemlaCategoryDot(CategoryColors.parse(category?.colorHex ?: DEFAULT_DOT), Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(charge.name, style = NemlaTheme.typography.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Caption(
                stringResource(
                    Res.string.recurring_row_meta,
                    frequency,
                    charge.dayOfMonth,
                    category?.name ?: stringResource(Res.string.recurring_uncategorized),
                ),
            )
        }
        Text(
            money(charge.amountCents, state.currency),
            style =
                NemlaTheme.typography.body
                    .copy(fontWeight = FontWeight.SemiBold)
                    .tabular(),
        )
        NemlaIconButton(
            icon = NemlaIcons.Trash2,
            contentDescription = stringResource(Res.string.recurring_delete, charge.name),
            onClick = { onAction(RecurringAction.DeleteClicked(charge.id)) },
            enabled = charge.id !in state.deleting,
            tint = NemlaTheme.colors.destructive,
        )
    }
}

private const val DEFAULT_DOT = "#64748B"

@Composable
private fun frequencyOptions(): List<NemlaOption<ChargeFrequency>> =
    listOf(
        NemlaOption(ChargeFrequency.MONTHLY, stringResource(Res.string.recurring_monthly)),
        NemlaOption(ChargeFrequency.YEARLY, stringResource(Res.string.recurring_yearly)),
    )

@Composable
private fun RecurringMessage.text(): String =
    when (this) {
        RecurringMessage.NothingDetected -> stringResource(Res.string.recurring_nothing_detected)
        RecurringMessage.AnalysisFailed -> stringResource(Res.string.recurring_analysis_failed)
        RecurringMessage.Saved -> stringResource(Res.string.recurring_saved)
        RecurringMessage.SaveFailed -> stringResource(Res.string.recurring_save_failed)
        RecurringMessage.Deleted -> stringResource(Res.string.recurring_deleted)
        RecurringMessage.DeleteFailed -> stringResource(Res.string.recurring_delete_failed)
        is RecurringMessage.Dictation -> text
    }
