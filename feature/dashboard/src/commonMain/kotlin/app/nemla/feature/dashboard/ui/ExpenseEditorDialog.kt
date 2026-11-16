package app.nemla.feature.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.component.NemlaBanner
import app.nemla.core.designsystem.component.NemlaBannerKind
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaDialog
import app.nemla.core.designsystem.component.NemlaOption
import app.nemla.core.designsystem.component.NemlaSelectField
import app.nemla.core.designsystem.component.NemlaTextField
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.PaymentMethod
import app.nemla.feature.dashboard.DashboardAction
import app.nemla.feature.dashboard.DashboardUiState
import app.nemla.feature.dashboard.EditorError
import app.nemla.feature.dashboard.ExpenseEditorState
import app.nemla.feature.dashboard.resources.Res
import app.nemla.feature.dashboard.resources.editor_amount
import app.nemla.feature.dashboard.resources.editor_amount_invalid
import app.nemla.feature.dashboard.resources.editor_cancel
import app.nemla.feature.dashboard.resources.editor_category
import app.nemla.feature.dashboard.resources.editor_date
import app.nemla.feature.dashboard.resources.editor_debit_hint
import app.nemla.feature.dashboard.resources.editor_delete
import app.nemla.feature.dashboard.resources.editor_delete_confirm
import app.nemla.feature.dashboard.resources.editor_delete_failed
import app.nemla.feature.dashboard.resources.editor_description
import app.nemla.feature.dashboard.resources.editor_label_readonly
import app.nemla.feature.dashboard.resources.editor_offline
import app.nemla.feature.dashboard.resources.editor_payment
import app.nemla.feature.dashboard.resources.editor_save
import app.nemla.feature.dashboard.resources.editor_save_failed
import app.nemla.feature.dashboard.resources.editor_timeout
import app.nemla.feature.dashboard.resources.editor_title
import app.nemla.feature.dashboard.resources.expense_fallback
import app.nemla.feature.dashboard.resources.no_category
import app.nemla.feature.dashboard.resources.payment_card
import app.nemla.feature.dashboard.resources.payment_cash
import app.nemla.feature.dashboard.resources.payment_cheque
import app.nemla.feature.dashboard.resources.payment_transfer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val EditorMaxWidth = 512.dp
private const val NO_CATEGORY_KEY = ""

@Composable
internal fun ExpenseEditorDialog(
    editor: ExpenseEditorState,
    state: DashboardUiState,
    format: DashboardFormat,
    onAction: (DashboardAction) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val colors = NemlaTheme.colors
    val enabled = !editor.isBusy
    NemlaDialog(
        title = stringResource(Res.string.editor_title),
        onDismissRequest = { onAction(DashboardAction.EditorDismissed) },
        dismissible = enabled,
        maxWidth = EditorMaxWidth,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.fieldGap)) {
            Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs)) {
                Text(stringResource(Res.string.editor_description), style = NemlaTheme.typography.label)
                Text(
                    text = editor.label?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.expense_fallback),
                    style = NemlaTheme.typography.input,
                )
                MutedCaption(stringResource(Res.string.editor_label_readonly))
            }
            NemlaTextField(
                value = editor.amountText,
                onValueChange = { onAction(DashboardAction.EditorAmountChanged(it)) },
                label = stringResource(Res.string.editor_amount, state.currency.symbol),
                error = if (editor.amountInvalid) stringResource(Res.string.editor_amount_invalid) else null,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            NemlaSelectField(
                label = stringResource(Res.string.editor_category),
                placeholder = stringResource(Res.string.no_category),
                options =
                    listOf(NemlaOption(NO_CATEGORY_KEY, stringResource(Res.string.no_category))) +
                        state.categories.map { NemlaOption(it.id.value, it.name) },
                selected = editor.categoryId?.value ?: NO_CATEGORY_KEY,
                onSelect = { key: String ->
                    onAction(DashboardAction.EditorCategorySelected(key.takeIf { it != NO_CATEGORY_KEY }?.let(::CategoryId)))
                },
                enabled = enabled,
            )
            DateFieldButton(
                label = stringResource(Res.string.editor_date),
                shown = format.long(editor.date),
                enabled = enabled,
                onClick = { picking = true },
            )
            NemlaSelectField(
                label = stringResource(Res.string.editor_payment),
                placeholder = stringResource(Res.string.editor_payment),
                options = PaymentMethod.entries.map { NemlaOption(it, stringResource(it.label())) },
                selected = editor.paymentMethod,
                onSelect = { method: PaymentMethod -> onAction(DashboardAction.EditorPaymentSelected(method)) },
                enabled = enabled,
            )
            editor.debitDate?.let { date ->
                Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(NemlaIcons.CreditCard, contentDescription = null, tint = colors.mutedForeground, modifier = Modifier.size(14.dp))
                    MutedCaption(stringResource(Res.string.editor_debit_hint, format.long(date)))
                }
            }
            editor.error?.let { NemlaBanner(message = stringResource(it.resource()), kind = NemlaBannerKind.Error) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NemlaButton(
                    text = stringResource(Res.string.editor_delete),
                    onClick = { onAction(DashboardAction.EditorDeleteClicked) },
                    variant = NemlaButtonVariant.Ghost,
                    enabled = enabled,
                    loading = editor.isDeleting,
                    leadingIcon = NemlaIcons.Trash2,
                )
                NemlaButton(
                    text = stringResource(Res.string.editor_save),
                    onClick = { onAction(DashboardAction.EditorSaveClicked) },
                    enabled = enabled,
                    loading = editor.isSaving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (picking) {
        PastDatePickerDialog(
            initial = editor.date,
            today = state.today,
            onPicked = { onAction(DashboardAction.EditorDateSelected(it)) },
            onDismiss = { picking = false },
        )
    }
    if (editor.confirmingDelete) {
        AlertDialog(
            onDismissRequest = { onAction(DashboardAction.EditorDeleteCancelled) },
            title = { Text(stringResource(Res.string.editor_delete_confirm), style = NemlaTheme.typography.cardTitle) },
            confirmButton = {
                NemlaButton(
                    text = stringResource(Res.string.editor_delete),
                    onClick = { onAction(DashboardAction.EditorDeleteConfirmed) },
                    variant = NemlaButtonVariant.Destructive,
                )
            },
            dismissButton = {
                NemlaButton(
                    text = stringResource(Res.string.editor_cancel),
                    onClick = { onAction(DashboardAction.EditorDeleteCancelled) },
                    variant = NemlaButtonVariant.Ghost,
                )
            },
            containerColor = colors.background,
        )
    }
}

private fun PaymentMethod.label(): StringResource =
    when (this) {
        PaymentMethod.CARD -> Res.string.payment_card
        PaymentMethod.TRANSFER -> Res.string.payment_transfer
        PaymentMethod.CASH -> Res.string.payment_cash
        PaymentMethod.CHEQUE -> Res.string.payment_cheque
    }

private fun EditorError.resource(): StringResource =
    when (this) {
        EditorError.SAVE_FAILED -> Res.string.editor_save_failed
        EditorError.DELETE_FAILED -> Res.string.editor_delete_failed
        EditorError.OFFLINE -> Res.string.editor_offline
        EditorError.TIMEOUT -> Res.string.editor_timeout
    }
