package app.nemla.feature.dashboard.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaCategoryDot
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.CategoryColors
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.format.DateLabels
import app.nemla.core.domain.format.MoneyFormat
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.ExpenseSource
import app.nemla.feature.dashboard.DashboardAction
import app.nemla.feature.dashboard.logic.ExpenseRow
import app.nemla.feature.dashboard.resources.Res
import app.nemla.feature.dashboard.resources.dashboard_date_cancel
import app.nemla.feature.dashboard.resources.dashboard_date_ok
import app.nemla.feature.dashboard.resources.expense_fallback
import app.nemla.feature.dashboard.resources.expense_row_meta
import app.nemla.feature.dashboard.resources.no_category
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

internal class DashboardFormat(
    val language: AppLanguage,
    val currency: Currency,
) {
    fun money(cents: Long): String = MoneyFormat.format(cents, currency)

    fun compact(cents: Long): String = MoneyFormat.compact(cents, currency)

    fun short(date: LocalDate): String = DateLabels.short(date, language)

    fun long(date: LocalDate): String = DateLabels.long(date, language)

    fun monthTitle(month: YearMonth): String = DateLabels.capitalizeFirst(DateLabels.monthYear(month, language))

    fun monthShort(monthNumber: Int): String = DateLabels.monthShort(monthNumber, language)

    fun monthInitial(monthNumber: Int): String = DateLabels.monthInitial(monthNumber, language)
}

private const val TABULAR_NUMBERS = "tnum"

internal fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR_NUMBERS)

@Composable
internal fun CardTitle(text: String) {
    Text(
        text = text,
        style = NemlaTheme.typography.cardTitle,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
internal fun MutedCaption(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = NemlaTheme.typography.caption.tabular(),
        color = NemlaTheme.colors.mutedForeground,
        modifier = modifier,
    )
}

@Composable
internal fun ExpenseRowItem(
    row: ExpenseRow,
    format: DashboardFormat,
    onAction: (DashboardAction) -> Unit,
) {
    val colors = NemlaTheme.colors
    val label = row.label?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.expense_fallback)
    val category = row.categoryName ?: stringResource(Res.string.no_category)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NemlaTheme.radius.lg))
                .clickable(role = Role.Button) { onAction(DashboardAction.ExpenseClicked(row.id)) }
                .padding(vertical = NemlaTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NemlaCategoryDot(
            color = if (row.colorHex == null) CategoryColors.Fallback else CategoryColors.parse(row.colorHex),
            modifier = Modifier.size(16.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = NemlaTheme.typography.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (row.source == ExpenseSource.VOICE) NemlaIcons.Mic else NemlaIcons.Pencil,
                    contentDescription = null,
                    tint = colors.mutedForeground,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = stringResource(Res.string.expense_row_meta, format.short(row.date), category),
                    style = NemlaTheme.typography.caption,
                    color = colors.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = format.money(row.amountCents),
            style =
                NemlaTheme.typography.body
                    .copy(fontWeight = FontWeight.SemiBold)
                    .tabular(),
        )
    }
}

@Composable
internal fun DateFieldButton(
    label: String,
    shown: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NemlaTheme.colors
    val shape = RoundedCornerShape(NemlaTheme.radius.md)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.labelGap)) {
        Text(label, style = NemlaTheme.typography.label, modifier = Modifier.clearAndSetSemantics { })
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = NemlaTheme.sizes.controlHeight)
                    .clip(shape)
                    .border(1.dp, colors.input, shape)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    .semantics {
                        contentDescription = label
                        stateDescription = shown
                    }.padding(horizontal = NemlaTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
        ) {
            Text(shown, style = NemlaTheme.typography.input.tabular(), modifier = Modifier.weight(1f).clearAndSetSemantics { })
            Icon(NemlaIcons.Calendar, contentDescription = null, modifier = Modifier.size(NemlaTheme.sizes.icon))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PastDatePickerDialog(
    initial: LocalDate,
    today: LocalDate,
    onPicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayMillis = today.toUtcMillis()
    val pickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initial.toUtcMillis(),
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
                text = stringResource(Res.string.dashboard_date_ok),
                onClick = {
                    pickerState.selectedDateMillis?.let { onPicked(it.toUtcDate()) }
                    onDismiss()
                },
                variant = NemlaButtonVariant.Ghost,
            )
        },
        dismissButton = {
            NemlaButton(text = stringResource(Res.string.dashboard_date_cancel), onClick = onDismiss, variant = NemlaButtonVariant.Ghost)
        },
    ) {
        DatePicker(state = pickerState)
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toUtcDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
