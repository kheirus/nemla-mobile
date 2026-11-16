package app.nemla.feature.voice.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.nemla.core.designsystem.component.NemlaBanner
import app.nemla.core.designsystem.component.NemlaBannerKind
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaCard
import app.nemla.core.designsystem.component.NemlaLoadingIndicator
import app.nemla.core.designsystem.component.NemlaOption
import app.nemla.core.designsystem.component.NemlaScreen
import app.nemla.core.designsystem.component.NemlaSelectField
import app.nemla.core.designsystem.component.NemlaTextField
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.ExpenseId
import app.nemla.feature.voice.resources.Res
import app.nemla.feature.voice.resources.review_amount
import app.nemla.feature.voice.resources.review_categories_failed
import app.nemla.feature.voice.resources.review_category
import app.nemla.feature.voice.resources.review_confirm
import app.nemla.feature.voice.resources.review_create_category_hint
import app.nemla.feature.voice.resources.review_date
import app.nemla.feature.voice.resources.review_date_cancel
import app.nemla.feature.voice.resources.review_date_ok
import app.nemla.feature.voice.resources.review_delete
import app.nemla.feature.voice.resources.review_delete_failed
import app.nemla.feature.voice.resources.review_empty
import app.nemla.feature.voice.resources.review_later
import app.nemla.feature.voice.resources.review_learned
import app.nemla.feature.voice.resources.review_loading
import app.nemla.feature.voice.resources.review_save_failed
import app.nemla.feature.voice.resources.review_title
import app.nemla.feature.voice.resources.review_transcript
import app.nemla.feature.voice.resources.review_unclear_label
import app.nemla.feature.voice.resources.voice_detected
import app.nemla.feature.voice.resources.voice_error_offline
import app.nemla.feature.voice.resources.voice_error_timeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

@Composable
internal fun VoiceReviewRoute(
    onClose: () -> Unit,
    viewModel: VoiceReviewViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    VoiceReviewEffect.Close -> currentOnClose()
                }
            }
        }
    }
    VoiceReviewScreen(state = state, onAction = viewModel::onAction)
}

@Composable
internal fun VoiceReviewScreen(
    state: VoiceReviewUiState,
    onAction: (VoiceReviewAction) -> Unit,
) {
    NemlaScreen {
        state.detectedCount?.let { count ->
            NemlaBanner(
                message = pluralStringResource(Res.plurals.voice_detected, count, count),
                kind = NemlaBannerKind.Success,
            )
        }
        state.message?.let { message ->
            NemlaBanner(message = stringResource(message.resource()), kind = NemlaBannerKind.Error)
        }
        NemlaCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm)) {
                Text(
                    text = stringResource(Res.string.review_title),
                    style = NemlaTheme.typography.cardTitle,
                    modifier = Modifier.semantics { heading() },
                )
                state.transcript?.let { transcript ->
                    Text(
                        text = stringResource(Res.string.review_transcript, transcript),
                        style = NemlaTheme.typography.body.copy(fontStyle = FontStyle.Italic),
                        color = NemlaTheme.colors.mutedForeground,
                    )
                }
                when {
                    state.isLoading -> LoadingRow()
                    state.lines.isEmpty() -> EmptyRow()
                    else -> state.lines.forEach { line -> ReviewLineTile(line, state, onAction) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = NemlaTheme.spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
                ) {
                    NemlaButton(
                        text = stringResource(Res.string.review_confirm),
                        onClick = { onAction(VoiceReviewAction.ConfirmClicked) },
                        enabled = state.canConfirm,
                        loading = state.isSaving,
                        leadingIcon = NemlaIcons.Check,
                    )
                    NemlaButton(
                        text = stringResource(Res.string.review_later),
                        onClick = { onAction(VoiceReviewAction.LaterClicked) },
                        enabled = !state.isSaving,
                        variant = NemlaButtonVariant.Ghost,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = NemlaTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NemlaLoadingIndicator(modifier = Modifier.size(NemlaTheme.sizes.iconLarge))
        Text(stringResource(Res.string.review_loading), style = NemlaTheme.typography.body, color = NemlaTheme.colors.mutedForeground)
    }
}

@Composable
private fun EmptyRow() {
    Text(
        text = stringResource(Res.string.review_empty),
        style = NemlaTheme.typography.body,
        color = NemlaTheme.colors.mutedForeground,
        modifier = Modifier.padding(vertical = NemlaTheme.spacing.md),
    )
}

@Composable
private fun ReviewLineTile(
    line: ReviewLine,
    state: VoiceReviewUiState,
    onAction: (VoiceReviewAction) -> Unit,
) {
    val colors = NemlaTheme.colors
    val editable = !state.isSaving && !line.isDeleting
    Surface(
        shape = RoundedCornerShape(NemlaTheme.radius.md),
        color = colors.card,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(NemlaTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = line.label,
                    style = NemlaTheme.typography.bodyStrong,
                    modifier = Modifier.weight(1f).padding(bottom = NemlaTheme.spacing.sm),
                )
                NemlaTextField(
                    value = line.amountText,
                    onValueChange = { onAction(VoiceReviewAction.AmountChanged(line.id, it)) },
                    label = stringResource(Res.string.review_amount),
                    enabled = editable,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(AMOUNT_WIDTH),
                )
            }
            if (line.categoryId == null) {
                WarningText(stringResource(Res.string.review_unclear_label))
                Text(
                    text = stringResource(Res.string.review_create_category_hint),
                    style = NemlaTheme.typography.caption,
                    color = colors.mutedForeground,
                )
            }
            if (line.learned) LearnedText()
            NemlaSelectField(
                label = stringResource(Res.string.review_category),
                placeholder = stringResource(Res.string.review_category),
                options = state.categories.map { NemlaOption(it.id, it.name) },
                selected = line.categoryId,
                onSelect = { id: CategoryId -> onAction(VoiceReviewAction.CategorySelected(line.id, id)) },
                enabled = editable,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
                DateField(
                    date = line.date,
                    today = state.today,
                    enabled = editable,
                    onPicked = { onAction(VoiceReviewAction.DateSelected(line.id, it)) },
                    modifier = Modifier.weight(1f),
                )
                DeleteButton(id = line.id, enabled = editable, onAction = onAction)
            }
        }
    }
}

private val AMOUNT_WIDTH = 128.dp

@Composable
private fun WarningText(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs), verticalAlignment = Alignment.Top) {
        Icon(
            NemlaIcons.AlertTriangle,
            contentDescription = null,
            tint = NemlaTheme.colors.onWarningContainer,
            modifier = Modifier.padding(top = 1.dp).size(14.dp),
        )
        Text(
            text = text,
            style = NemlaTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
            color = NemlaTheme.colors.onWarningContainer,
        )
    }
}

@Composable
private fun LearnedText() {
    Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Icon(NemlaIcons.Sparkles, contentDescription = null, tint = NemlaTheme.colors.accent, modifier = Modifier.size(12.dp))
        Text(
            text = stringResource(Res.string.review_learned),
            style = NemlaTheme.typography.caption,
            color = NemlaTheme.colors.mutedForeground,
        )
    }
}

@Composable
private fun DeleteButton(
    id: ExpenseId,
    enabled: Boolean,
    onAction: (VoiceReviewAction) -> Unit,
) {
    IconButton(onClick = { onAction(VoiceReviewAction.DeleteClicked(id)) }, enabled = enabled) {
        Icon(
            NemlaIcons.Trash2,
            contentDescription = stringResource(Res.string.review_delete),
            modifier = Modifier.size(NemlaTheme.sizes.icon),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    date: LocalDate,
    today: LocalDate,
    enabled: Boolean,
    onPicked: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = NemlaTheme.colors
    val shape = RoundedCornerShape(NemlaTheme.radius.md)
    val label = stringResource(Res.string.review_date)
    val shown = date.display()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.labelGap)) {
        Text(label, style = NemlaTheme.typography.label, modifier = Modifier.clearAndSetSemantics { })
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = NemlaTheme.sizes.controlHeight)
                    .clip(shape)
                    .border(1.dp, colors.input, shape)
                    .clickable(enabled = enabled, role = Role.Button) { open = true }
                    .semantics {
                        contentDescription = label
                        stateDescription = shown
                    }.padding(horizontal = NemlaTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
        ) {
            Text(
                shown,
                style = NemlaTheme.typography.input.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.weight(1f).clearAndSetSemantics { },
            )
            Icon(NemlaIcons.Calendar, contentDescription = null, modifier = Modifier.size(NemlaTheme.sizes.icon))
        }
    }
    if (open) {
        val todayMillis = today.toUtcMillis()
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = date.toUtcMillis(),
                selectableDates =
                    remember(todayMillis) {
                        object : SelectableDates {
                            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis

                            override fun isSelectableYear(year: Int): Boolean = year <= today.year
                        }
                    },
            )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                NemlaButton(
                    text = stringResource(Res.string.review_date_ok),
                    onClick = {
                        pickerState.selectedDateMillis?.let { onPicked(it.toUtcDate()) }
                        open = false
                    },
                    variant = NemlaButtonVariant.Ghost,
                )
            },
            dismissButton = {
                NemlaButton(
                    text = stringResource(Res.string.review_date_cancel),
                    onClick = { open = false },
                    variant = NemlaButtonVariant.Ghost,
                )
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toUtcDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

internal fun LocalDate.display(): String {
    val iso = toString()
    return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4)
}

private fun ReviewMessage.resource(): StringResource =
    when (this) {
        ReviewMessage.SAVE_FAILED -> Res.string.review_save_failed
        ReviewMessage.DELETE_FAILED -> Res.string.review_delete_failed
        ReviewMessage.CATEGORIES_UNAVAILABLE -> Res.string.review_categories_failed
        ReviewMessage.OFFLINE -> Res.string.voice_error_offline
        ReviewMessage.TIMEOUT -> Res.string.voice_error_timeout
    }

@Preview
@Composable
private fun VoiceReviewScreenPreview() {
    NemlaTheme {
        VoiceReviewScreen(
            state =
                VoiceReviewUiState(
                    today = LocalDate(2026, 11, 16),
                    isLoading = false,
                    transcript = "Marché douze euros, ciné quinze",
                    lines =
                        listOf(
                            ReviewLine(ExpenseId("1"), "Marché", "12,00", LocalDate(2026, 11, 16), CategoryId("c"), learned = true),
                            ReviewLine(ExpenseId("2"), "Ciné", "15,00", LocalDate(2026, 11, 16), null),
                        ),
                    categories = listOf(CategoryChoice(CategoryId("c"), "Courses")),
                    detectedCount = 2,
                ),
            onAction = {},
        )
    }
}
