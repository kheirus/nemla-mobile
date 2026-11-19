package app.nemla.feature.settings.card

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaChoiceCard
import app.nemla.core.designsystem.component.NemlaOption
import app.nemla.core.designsystem.component.NemlaPickerField
import app.nemla.core.designsystem.component.NemlaSelectField
import app.nemla.core.designsystem.component.NemlaTextField
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.format.DateLabels
import app.nemla.core.domain.model.Banks
import app.nemla.feature.settings.common.Caption
import app.nemla.feature.settings.common.NoticeBanner
import app.nemla.feature.settings.common.PanelCard
import app.nemla.feature.settings.resources.Res
import app.nemla.feature.settings.resources.card_all
import app.nemla.feature.settings.resources.card_all_help
import app.nemla.feature.settings.resources.card_applied_all
import app.nemla.feature.settings.resources.card_applied_from
import app.nemla.feature.settings.resources.card_bank
import app.nemla.feature.settings.resources.card_bank_help
import app.nemla.feature.settings.resources.card_bank_placeholder
import app.nemla.feature.settings.resources.card_cutoff
import app.nemla.feature.settings.resources.card_date
import app.nemla.feature.settings.resources.card_date_cancel
import app.nemla.feature.settings.resources.card_date_help
import app.nemla.feature.settings.resources.card_date_label
import app.nemla.feature.settings.resources.card_date_ok
import app.nemla.feature.settings.resources.card_debit
import app.nemla.feature.settings.resources.card_deferred
import app.nemla.feature.settings.resources.card_deferred_help
import app.nemla.feature.settings.resources.card_example
import app.nemla.feature.settings.resources.card_failed
import app.nemla.feature.settings.resources.card_immediate
import app.nemla.feature.settings.resources.card_immediate_help
import app.nemla.feature.settings.resources.card_immediate_saved
import app.nemla.feature.settings.resources.card_intro
import app.nemla.feature.settings.resources.card_moved
import app.nemla.feature.settings.resources.card_save
import app.nemla.feature.settings.resources.card_scope
import app.nemla.feature.settings.resources.card_title
import app.nemla.feature.settings.resources.card_today
import app.nemla.feature.settings.resources.card_today_help
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

@Composable
internal fun CardPanelRoute(viewModel: CardViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onAction(CardAction.Opened) }
    CardPanel(state = state, onAction = viewModel::onAction)
}

@Composable
internal fun CardPanel(
    state: CardUiState,
    onAction: (CardAction) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md)) {
        state.notice?.let { notice ->
            NoticeBanner(text = notice.message.text(state), kind = notice.kind, onDismiss = { onAction(CardAction.NoticeDismissed) })
        }
        PanelCard(title = stringResource(Res.string.card_title), subtitle = stringResource(Res.string.card_intro)) {
            Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
                NemlaChoiceCard(
                    title = stringResource(Res.string.card_immediate),
                    description = stringResource(Res.string.card_immediate_help),
                    selected = !state.deferred,
                    onClick = { onAction(CardAction.ModeSelected(false)) },
                    modifier = Modifier.weight(1f),
                )
                NemlaChoiceCard(
                    title = stringResource(Res.string.card_deferred),
                    description = stringResource(Res.string.card_deferred_help),
                    selected = state.deferred,
                    onClick = { onAction(CardAction.ModeSelected(true)) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.deferred) DeferredForm(state, onAction, onPickDate = { picking = true })
            NemlaButton(
                text = stringResource(Res.string.card_save),
                onClick = { onAction(CardAction.SaveClicked) },
                enabled = state.canSave,
                loading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (picking) {
        PastDatePicker(
            initial = state.date,
            today = state.today,
            onPicked = { onAction(CardAction.DateSelected(it)) },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun DeferredForm(
    state: CardUiState,
    onAction: (CardAction) -> Unit,
    onPickDate: () -> Unit,
) {
    val shape = RoundedCornerShape(NemlaTheme.radius.lg)
    Column(
        modifier = Modifier.fillMaxWidth().border(1.dp, NemlaTheme.colors.border, shape).padding(NemlaTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
    ) {
        NemlaSelectField(
            label = stringResource(Res.string.card_bank),
            placeholder = stringResource(Res.string.card_bank_placeholder),
            options = Banks.all.map { NemlaOption(it.id, it.name) },
            selected = state.bankId,
            onSelect = { onAction(CardAction.BankSelected(it)) },
        )
        Caption(stringResource(Res.string.card_bank_help))
        Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
            NemlaTextField(
                value = state.cutoffText,
                onValueChange = { onAction(CardAction.CutoffChanged(it)) },
                label = stringResource(Res.string.card_cutoff),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            NemlaTextField(
                value = state.debitText,
                onValueChange = { onAction(CardAction.DebitChanged(it)) },
                label = stringResource(Res.string.card_debit),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Text(stringResource(Res.string.card_scope), style = NemlaTheme.typography.bodyStrong)
        NemlaChoiceCard(
            title = stringResource(Res.string.card_today),
            description = stringResource(Res.string.card_today_help),
            selected = state.scope == DebitScope.TODAY,
            onClick = { onAction(CardAction.ScopeSelected(DebitScope.TODAY)) },
            modifier = Modifier.fillMaxWidth(),
        )
        NemlaChoiceCard(
            title = stringResource(Res.string.card_all),
            description = stringResource(Res.string.card_all_help),
            selected = state.scope == DebitScope.ALL,
            onClick = { onAction(CardAction.ScopeSelected(DebitScope.ALL)) },
            modifier = Modifier.fillMaxWidth(),
        )
        NemlaChoiceCard(
            title = stringResource(Res.string.card_date),
            description = stringResource(Res.string.card_date_help),
            selected = state.scope == DebitScope.DATE,
            onClick = { onAction(CardAction.ScopeSelected(DebitScope.DATE)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.scope == DebitScope.DATE) DateButton(DateLabels.long(state.date, state.language), onPickDate)
        Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs), verticalAlignment = Alignment.Top) {
            Icon(NemlaIcons.Info, contentDescription = null, tint = NemlaTheme.colors.mutedForeground, modifier = Modifier.size(14.dp))
            Caption(
                stringResource(Res.string.card_example, DateLabels.long(state.exampleDate, state.language)),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DateButton(
    shown: String,
    onClick: () -> Unit,
) {
    NemlaPickerField(label = stringResource(Res.string.card_date_label), value = shown, onClick = onClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastDatePicker(
    initial: LocalDate,
    today: LocalDate,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayMillis = today.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val pickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
            selectableDates =
                remember(todayMillis) {
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis

                        override fun isSelectableYear(year: Int): Boolean = year <= today.year
                    }
                },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            NemlaButton(
                text = stringResource(Res.string.card_date_ok),
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onPicked(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
                    }
                    onDismiss()
                },
                variant = NemlaButtonVariant.Ghost,
            )
        },
        dismissButton = {
            NemlaButton(text = stringResource(Res.string.card_date_cancel), onClick = onDismiss, variant = NemlaButtonVariant.Ghost)
        },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun CardMessage.text(state: CardUiState): String {
    val main =
        when (this) {
            is CardMessage.ImmediateSaved -> stringResource(Res.string.card_immediate_saved)
            is CardMessage.AppliedFrom -> stringResource(Res.string.card_applied_from, DateLabels.long(date, state.language))
            is CardMessage.AppliedToAll -> stringResource(Res.string.card_applied_all)
            CardMessage.Failed -> stringResource(Res.string.card_failed)
        }
    return if (moved > 0) main + "\n" + pluralStringResource(Res.plurals.card_moved, moved, moved) else main
}
