package app.nemla.feature.dashboard.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nemla.core.designsystem.component.NemlaAnimatedColumn
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaCard
import app.nemla.core.designsystem.component.NemlaCategoryDot
import app.nemla.core.designsystem.component.NemlaLoadingIndicator
import app.nemla.core.designsystem.component.NemlaSearchField
import app.nemla.core.designsystem.component.NemlaTextButton
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.CategoryColors
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.ReviewOrigin
import app.nemla.feature.dashboard.DashboardAction
import app.nemla.feature.dashboard.DashboardUiState
import app.nemla.feature.dashboard.logic.BudgetUsage
import app.nemla.feature.dashboard.logic.PendingGroup
import app.nemla.feature.dashboard.resources.Res
import app.nemla.feature.dashboard.resources.alerts_adjust
import app.nemla.feature.dashboard.resources.alerts_over
import app.nemla.feature.dashboard.resources.alerts_planned
import app.nemla.feature.dashboard.resources.alerts_title
import app.nemla.feature.dashboard.resources.debit_banner_body
import app.nemla.feature.dashboard.resources.debit_banner_title
import app.nemla.feature.dashboard.resources.page_indicator
import app.nemla.feature.dashboard.resources.page_next
import app.nemla.feature.dashboard.resources.page_previous
import app.nemla.feature.dashboard.resources.pending_body
import app.nemla.feature.dashboard.resources.pending_check
import app.nemla.feature.dashboard.resources.pending_import
import app.nemla.feature.dashboard.resources.pending_summary
import app.nemla.feature.dashboard.resources.pending_title
import app.nemla.feature.dashboard.resources.pending_voice
import app.nemla.feature.dashboard.resources.search_clear
import app.nemla.feature.dashboard.resources.search_empty
import app.nemla.feature.dashboard.resources.search_failed
import app.nemla.feature.dashboard.resources.search_pick_date
import app.nemla.feature.dashboard.resources.search_placeholder
import app.nemla.feature.dashboard.resources.search_results
import app.nemla.feature.dashboard.resources.search_text_soon
import app.nemla.feature.dashboard.resources.today_empty
import app.nemla.feature.dashboard.resources.today_summary
import app.nemla.feature.dashboard.resources.today_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val PENDING_BORDER_ALPHA = 0.50f
private const val ALERT_BORDER_ALPHA = 0.40f
private const val ALERT_BACKGROUND_ALPHA = 0.05f
private const val ALERT_LINE_ALPHA = 0.70f
private const val DEBIT_BANNER_ALPHA = 0.10f

@Composable
internal fun PendingCard(
    pending: List<PendingGroup>,
    format: DashboardFormat,
    onAction: (DashboardAction) -> Unit,
) {
    val colors = NemlaTheme.colors
    NemlaCard(modifier = Modifier.fillMaxWidth(), borderColor = colors.accent.copy(alpha = PENDING_BORDER_ALPHA)) {
        CardTitle(stringResource(Res.string.pending_title))
        Text(
            text = stringResource(Res.string.pending_body),
            style = NemlaTheme.typography.body,
            color = colors.mutedForeground,
            modifier = Modifier.padding(top = NemlaTheme.spacing.xxs),
        )
        NemlaAnimatedColumn(
            items = pending,
            key = { it.review },
            modifier = Modifier.padding(top = NemlaTheme.spacing.sm),
        ) { _, group, first ->
            Column {
                if (!first) HorizontalDivider(color = colors.border)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                val index = pending.indexOf(group)
                                if (index >= 0) onAction(DashboardAction.PendingGroupClicked(index))
                            }.padding(vertical = NemlaTheme.spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                stringResource(
                                    if (group.origin ==
                                        ReviewOrigin.IMPORT
                                    ) {
                                        Res.string.pending_import
                                    } else {
                                        Res.string.pending_voice
                                    },
                                ),
                            style = NemlaTheme.typography.bodyStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MutedCaption(
                            pluralStringResource(Res.plurals.pending_summary, group.count, group.count, format.money(group.totalCents)),
                        )
                    }
                    Surface(shape = CircleShape, color = colors.primary, contentColor = colors.primaryForeground) {
                        Row(
                            modifier = Modifier.padding(horizontal = NemlaTheme.spacing.sm, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(NemlaIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                stringResource(Res.string.pending_check),
                                style = NemlaTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AlertsCard(
    alerts: List<BudgetUsage>,
    format: DashboardFormat,
    onAdjust: () -> Unit,
) {
    val colors = NemlaTheme.colors
    NemlaCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = NemlaTheme.spacing.md,
        borderColor = colors.destructive.copy(alpha = ALERT_BORDER_ALPHA),
        containerColor = colors.destructive.copy(alpha = ALERT_BACKGROUND_ALPHA).compositeOver(colors.card),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(NemlaIcons.AlertTriangle, contentDescription = null, tint = colors.destructive, modifier = Modifier.size(16.dp))
            Text(
                text = pluralStringResource(Res.plurals.alerts_title, alerts.size, alerts.size),
                style = NemlaTheme.typography.bodyEmphasis,
                modifier = Modifier.semantics { heading() },
            )
        }
        Column(
            modifier = Modifier.padding(top = NemlaTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
        ) {
            alerts.forEach { alert ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(colors.background.copy(alpha = ALERT_LINE_ALPHA), RoundedCornerShape(NemlaTheme.radius.lg))
                            .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NemlaCategoryDot(CategoryColors.parse(alert.colorHex), Modifier.size(14.dp))
                    Text(
                        alert.name,
                        style = NemlaTheme.typography.bodyStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = format.money(alert.usedCents),
                            style = NemlaTheme.typography.bodyEmphasis.tabular(),
                            color = colors.destructive,
                        )
                        Text(
                            text =
                                if (alert.overCents > 0) {
                                    stringResource(
                                        Res.string.alerts_over,
                                        format.money(alert.overCents),
                                        format.money(alert.allocatedCents),
                                    )
                                } else {
                                    stringResource(Res.string.alerts_planned, format.money(alert.allocatedCents))
                                },
                            style =
                                NemlaTheme.typography.caption
                                    .copy(fontSize = 11.sp)
                                    .tabular(),
                            color = colors.mutedForeground,
                        )
                    }
                }
            }
        }
        NemlaTextButton(
            text = stringResource(Res.string.alerts_adjust),
            onClick = onAdjust,
            modifier = Modifier.padding(top = NemlaTheme.spacing.xs),
        )
    }
}

@Composable
internal fun SearchCard(
    state: DashboardUiState,
    format: DashboardFormat,
    onAction: (DashboardAction) -> Unit,
) {
    val colors = NemlaTheme.colors
    val search = state.search
    var picking by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    NemlaCard(modifier = Modifier.fillMaxWidth(), contentPadding = NemlaTheme.spacing.sm) {
        NemlaSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(Res.string.search_placeholder),
            trailingActions = {
                IconButton(onClick = { picking = true }) {
                    Icon(
                        NemlaIcons.CalendarDays,
                        contentDescription = stringResource(Res.string.search_pick_date),
                        tint = if (search.date != null) colors.primary else colors.foreground,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )
        AnimatedVisibility(visible = query.isNotBlank(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            MutedCaption(
                stringResource(Res.string.search_text_soon),
                modifier = Modifier.padding(top = NemlaTheme.spacing.xs, start = NemlaTheme.spacing.sm),
            )
        }
        search.date?.let { date ->
            InputChip(
                selected = true,
                onClick = { picking = true },
                label = { Text(format.short(date), style = NemlaTheme.typography.button.tabular()) },
                leadingIcon = { Icon(NemlaIcons.CalendarDays, contentDescription = null, modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    IconButton(onClick = { onAction(DashboardAction.SearchCleared) }, modifier = Modifier.size(24.dp)) {
                        Icon(NemlaIcons.X, contentDescription = stringResource(Res.string.search_clear), modifier = Modifier.size(16.dp))
                    }
                },
                colors =
                    InputChipDefaults.inputChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                modifier = Modifier.padding(top = NemlaTheme.spacing.xs),
            )
        }
        if (search.isActive) {
            Column(modifier = Modifier.padding(top = NemlaTheme.spacing.xs)) {
                val results = search.results
                when {
                    search.isLoading && results == null -> {
                        NemlaLoadingIndicator(modifier = Modifier.size(20.dp))
                    }

                    search.failed -> {
                        MutedCaption(stringResource(Res.string.search_failed))
                    }

                    results.isNullOrEmpty() -> {
                        MutedCaption(stringResource(Res.string.search_empty))
                    }

                    else -> {
                        MutedCaption(
                            pluralStringResource(Res.plurals.search_results, results.size, results.size, format.money(search.totalCents)),
                        )
                        results.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider(color = colors.border)
                            ExpenseRowItem(row, format, onAction)
                        }
                    }
                }
            }
        }
    }
    if (picking) {
        PastDatePickerDialog(
            initial = search.date ?: state.today,
            today = state.today,
            onPicked = { onAction(DashboardAction.SearchDateSelected(it)) },
            onDismiss = { picking = false },
        )
    }
}

@Composable
internal fun TodayCard(
    state: DashboardUiState,
    format: DashboardFormat,
    onAction: (DashboardAction) -> Unit,
) {
    val colors = NemlaTheme.colors
    val today = state.view.today
    NemlaCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm)) {
            Column(modifier = Modifier.weight(1f)) { CardTitle(stringResource(Res.string.today_title)) }
            MutedCaption(pluralStringResource(Res.plurals.today_summary, today.rows.size, today.rows.size, format.money(today.totalCents)))
        }
        state.view.todayDebitDate?.let { date ->
            val title = stringResource(Res.string.debit_banner_title)
            val body = stringResource(Res.string.debit_banner_body, format.long(date))
            Text(
                text =
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(title) }
                        append(" ")
                        append(body)
                    },
                style = NemlaTheme.typography.caption,
                modifier =
                    Modifier
                        .padding(top = NemlaTheme.spacing.sm)
                        .fillMaxWidth()
                        .background(colors.primary.copy(alpha = DEBIT_BANNER_ALPHA), RoundedCornerShape(NemlaTheme.radius.lg))
                        .padding(horizontal = NemlaTheme.spacing.sm, vertical = NemlaTheme.spacing.xs),
            )
        }
        Column(modifier = Modifier.padding(top = NemlaTheme.spacing.sm)) {
            if (today.rows.isEmpty()) {
                MutedCaption(stringResource(Res.string.today_empty))
            } else {
                key(today.currentPage) {
                    NemlaAnimatedColumn(items = today.pageRows, key = { it.id }) { _, row, first ->
                        Column {
                            if (!first) HorizontalDivider(color = colors.border)
                            ExpenseRowItem(row, format, onAction)
                        }
                    }
                }
            }
        }
        if (today.rows.size > TODAY_PAGE_THRESHOLD) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = NemlaTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                NemlaButton(
                    text = stringResource(Res.string.page_previous),
                    onClick = { onAction(DashboardAction.TodayPreviousPage) },
                    variant = NemlaButtonVariant.Secondary,
                    enabled = today.hasPrevious,
                    leadingIcon = NemlaIcons.ChevronLeft,
                )
                MutedCaption(stringResource(Res.string.page_indicator, today.currentPage + 1, today.pageCount))
                NemlaButton(
                    text = stringResource(Res.string.page_next),
                    onClick = { onAction(DashboardAction.TodayNextPage) },
                    variant = NemlaButtonVariant.Secondary,
                    enabled = today.hasNext,
                    leadingIcon = NemlaIcons.ChevronRight,
                )
            }
        }
    }
}

private const val TODAY_PAGE_THRESHOLD = 5
