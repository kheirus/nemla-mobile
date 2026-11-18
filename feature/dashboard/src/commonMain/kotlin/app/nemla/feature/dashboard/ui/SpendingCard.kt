package app.nemla.feature.dashboard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.component.NemlaCard
import app.nemla.core.designsystem.component.NemlaCategoryDot
import app.nemla.core.designsystem.component.NemlaFormSheet
import app.nemla.core.designsystem.component.NemlaOption
import app.nemla.core.designsystem.component.NemlaProgressBar
import app.nemla.core.designsystem.component.NemlaSegmentedButtons
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.CategoryColors
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.feature.dashboard.DashboardAction
import app.nemla.feature.dashboard.DashboardUiState
import app.nemla.feature.dashboard.SpendingTab
import app.nemla.feature.dashboard.logic.BudgetUsage
import app.nemla.feature.dashboard.logic.SpendingGroup
import app.nemla.feature.dashboard.resources.Res
import app.nemla.feature.dashboard.resources.budget_usage
import app.nemla.feature.dashboard.resources.budget_usage_over
import app.nemla.feature.dashboard.resources.budgets_title
import app.nemla.feature.dashboard.resources.expense_fallback
import app.nemla.feature.dashboard.resources.expense_row_meta
import app.nemla.feature.dashboard.resources.other_group
import app.nemla.feature.dashboard.resources.spending_chart_description
import app.nemla.feature.dashboard.resources.spending_empty
import app.nemla.feature.dashboard.resources.spending_percent
import app.nemla.feature.dashboard.resources.spending_title
import app.nemla.feature.dashboard.resources.tab_breakdown
import app.nemla.feature.dashboard.resources.tab_budgets
import org.jetbrains.compose.resources.stringResource
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val DonutHeight = 192.dp
private const val INNER_RADIUS = 0.58f
private const val OUTER_RADIUS = 0.88f
private const val FULL_TURN = 360f
private const val PERCENT = 100.0
private const val MIN_LABEL_SWEEP = 18f
private const val DEGREES_PER_RADIAN = 57.29578f
private const val TOUCH_SLACK_IN = 0.8f
private const val TOUCH_SLACK_OUT = 1.1f

@Composable
internal fun SpendingCard(
    state: DashboardUiState,
    format: DashboardFormat,
    onAction: (DashboardAction) -> Unit,
) {
    val spending = state.view.spending
    val tab = state.effectiveSpendingTab
    NemlaCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(if (tab == SpendingTab.BUDGETS) Res.string.budgets_title else Res.string.spending_title))
        if (spending.hasBudgets) {
            NemlaSegmentedButtons(
                options =
                    listOf(
                        NemlaOption(SpendingTab.BREAKDOWN, stringResource(Res.string.tab_breakdown)),
                        NemlaOption(SpendingTab.BUDGETS, stringResource(Res.string.tab_budgets)),
                    ),
                selected = tab,
                onSelect = { onAction(DashboardAction.SpendingTabSelected(it)) },
                modifier = Modifier.fillMaxWidth().padding(top = NemlaTheme.spacing.sm),
            )
        }
        Column(
            modifier = Modifier.padding(top = NemlaTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
        ) {
            when {
                tab == SpendingTab.BUDGETS -> spending.budgets.forEach { BudgetRow(it, format) }
                spending.groups.isEmpty() -> MutedCaption(stringResource(Res.string.spending_empty))
                else -> Breakdown(spending.groups, state.expandedGroup, format, onAction)
            }
        }
    }
}

@Composable
private fun Breakdown(
    groups: List<SpendingGroup>,
    expanded: String?,
    format: DashboardFormat,
    onAction: (DashboardAction) -> Unit,
) {
    val total = groups.sumOf { it.totalCents.coerceAtLeast(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    Donut(groups, total, onSliceClick = { selected = it })
    groups.forEach { group ->
        GroupRow(group, sharePercent(group.totalCents, total), group.key == expanded, format, onAction)
    }
    groups.firstOrNull { it.key == selected }?.let { group ->
        CategorySheet(group, sharePercent(group.totalCents, total), format, onAction, onDismiss = { selected = null })
    }
}

private fun sharePercent(
    cents: Long,
    total: Long,
): Int = if (total <= 0 || cents <= 0) 0 else (cents.toDouble() * PERCENT / total.toDouble()).roundToInt()

private class Slice(
    val group: SpendingGroup,
    val start: Float,
    val sweep: Float,
    val percent: Int,
)

private fun slices(
    groups: List<SpendingGroup>,
    total: Long,
): List<Slice> {
    var start = 0f
    return groups.filter { it.totalCents > 0 }.map { group ->
        val sweep = (group.totalCents.toDouble() / total.toDouble() * FULL_TURN).toFloat()
        Slice(group, start, sweep, sharePercent(group.totalCents, total)).also { start += sweep }
    }
}

@Composable
private fun Donut(
    groups: List<SpendingGroup>,
    total: Long,
    onSliceClick: (String) -> Unit,
) {
    val description = stringResource(Res.string.spending_chart_description)
    val slices = remember(groups, total) { if (total > 0) slices(groups, total) else emptyList() }
    val labels = slices.map { stringResource(Res.string.spending_percent, it.percent.toString()) }
    val measurer = rememberTextMeasurer()
    val labelStyle = NemlaTheme.typography.caption.copy(fontWeight = FontWeight.Bold, color = Color.White)
    val haptics = LocalHapticFeedback.current
    val currentOnClick by rememberUpdatedState(onSliceClick)
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(sweep) { sweep.animateTo(FULL_TURN, tween(DONUT_SWEEP_MILLIS, easing = FastOutSlowInEasing)) }
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(DonutHeight)
                .semantics { contentDescription = description }
                .pointerInput(slices) {
                    detectTapGestures { tap ->
                        val half = minOf(size.width, size.height) / 2f
                        val dx = tap.x - size.width / 2f
                        val dy = tap.y - size.height / 2f
                        val distance = sqrt(dx * dx + dy * dy)
                        if (distance < half * INNER_RADIUS * TOUCH_SLACK_IN || distance > half * OUTER_RADIUS * TOUCH_SLACK_OUT) {
                            return@detectTapGestures
                        }
                        val angle = ((-atan2(dy, dx) * DEGREES_PER_RADIAN) % FULL_TURN + FULL_TURN) % FULL_TURN
                        slices.firstOrNull { angle >= it.start && angle < it.start + it.sweep }?.let {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnClick(it.group.key)
                        }
                    }
                },
    ) {
        if (slices.isEmpty()) return@Canvas
        val half = minOf(size.width, size.height) / 2f
        val outer = half * OUTER_RADIUS
        val inner = half * INNER_RADIUS
        val ringRadius = (outer + inner) / 2f
        val thickness = outer - inner
        val topLeft = Offset(center.x - ringRadius, center.y - ringRadius)
        val limit = sweep.value
        slices.forEachIndexed { index, slice ->
            val visible = minOf(slice.sweep, limit - slice.start)
            if (visible <= 0f) return@forEachIndexed
            drawArc(
                color = slice.group.colorOrOther(),
                startAngle = -slice.start,
                sweepAngle = -visible,
                useCenter = false,
                topLeft = topLeft,
                size = Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(width = thickness),
            )
            if (visible >= slice.sweep && slice.sweep >= MIN_LABEL_SWEEP) {
                val layout = measurer.measure(labels[index], labelStyle)
                val middle = -(slice.start + slice.sweep / 2f) / DEGREES_PER_RADIAN
                val anchor = Offset(center.x + ringRadius * cos(middle), center.y + ringRadius * sin(middle))
                drawText(
                    layout,
                    topLeft = Offset(anchor.x - layout.size.width / 2f, anchor.y - layout.size.height / 2f),
                )
            }
        }
    }
}

@Composable
private fun CategorySheet(
    group: SpendingGroup,
    percent: Int,
    format: DashboardFormat,
    onAction: (DashboardAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val name = if (group.isOther) stringResource(Res.string.other_group) else group.name.orEmpty()
    NemlaFormSheet(
        title = name,
        description =
            stringResource(
                Res.string.expense_row_meta,
                format.money(group.totalCents),
                stringResource(Res.string.spending_percent, percent.toString()),
            ),
        onDismissRequest = onDismiss,
    ) { sheet ->
        Column {
            group.rows.forEach { row ->
                val label = row.label?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.expense_fallback)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = NemlaTheme.sizes.minTouchTarget)
                            .clip(RoundedCornerShape(NemlaTheme.radius.md))
                            .clickable(role = Role.Button) { sheet.dismiss { onAction(DashboardAction.ExpenseClicked(row.id)) } }
                            .padding(horizontal = NemlaTheme.spacing.xs, vertical = NemlaTheme.spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NemlaCategoryDot(group.colorOrOther(), Modifier.size(12.dp))
                    Text(
                        text = stringResource(Res.string.expense_row_meta, format.short(row.date), label),
                        style = NemlaTheme.typography.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(format.money(row.amountCents), style = NemlaTheme.typography.body.tabular())
                }
            }
        }
    }
}

private fun SpendingGroup.colorOrOther() = if (isOther || colorHex == null) CategoryColors.OtherGroup else CategoryColors.parse(colorHex)

@Composable
private fun GroupRow(
    group: SpendingGroup,
    percent: Int,
    isExpanded: Boolean,
    format: DashboardFormat,
    onAction: (DashboardAction) -> Unit,
) {
    val colors = NemlaTheme.colors
    val name = if (group.isOther) stringResource(Res.string.other_group) else group.name.orEmpty()
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = NemlaTheme.sizes.minTouchTarget)
                    .clip(RoundedCornerShape(NemlaTheme.radius.lg))
                    .clickable(role = Role.Button) { onAction(DashboardAction.GroupToggled(group.key)) }
                    .padding(horizontal = NemlaTheme.spacing.sm, vertical = NemlaTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NemlaCategoryDot(group.colorOrOther(), Modifier.size(16.dp))
            Text(name, style = NemlaTheme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                stringResource(Res.string.spending_percent, percent.toString()),
                style = NemlaTheme.typography.caption.tabular(),
                color = colors.mutedForeground,
            )
            Text(format.money(group.totalCents), style = NemlaTheme.typography.body.tabular(), color = colors.mutedForeground)
            Icon(
                imageVector = if (isExpanded) NemlaIcons.ChevronDown else NemlaIcons.ChevronRight,
                contentDescription = null,
                tint = colors.mutedForeground,
                modifier = Modifier.size(16.dp),
            )
        }
        if (isExpanded) {
            Row(modifier = Modifier.padding(start = NemlaTheme.spacing.xl).height(IntrinsicSize.Min)) {
                VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp), color = colors.border)
                Column(modifier = Modifier.padding(start = NemlaTheme.spacing.sm).weight(1f)) {
                    group.rows.forEach { row ->
                        val label = row.label?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.expense_fallback)
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 40.dp)
                                    .clip(RoundedCornerShape(NemlaTheme.radius.sm))
                                    .clickable(role = Role.Button) { onAction(DashboardAction.ExpenseClicked(row.id)) }
                                    .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
                        ) {
                            Text(
                                text = stringResource(Res.string.expense_row_meta, format.short(row.date), label),
                                style = NemlaTheme.typography.caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(format.money(row.amountCents), style = NemlaTheme.typography.caption.tabular())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(
    usage: BudgetUsage,
    format: DashboardFormat,
) {
    val colors = NemlaTheme.colors
    Column(modifier = Modifier.padding(vertical = NemlaTheme.spacing.xxs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            NemlaCategoryDot(CategoryColors.parse(usage.colorHex), Modifier.size(16.dp))
            Text(
                usage.name,
                style = NemlaTheme.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    if (usage.isOver) {
                        stringResource(
                            Res.string.budget_usage_over,
                            format.money(usage.usedCents),
                            format.money(usage.allocatedCents),
                            format.money(usage.overCents),
                        )
                    } else {
                        stringResource(Res.string.budget_usage, format.money(usage.usedCents), format.money(usage.allocatedCents))
                    },
                style =
                    NemlaTheme.typography.caption
                        .copy(fontWeight = FontWeight.Medium)
                        .tabular(),
                color = if (usage.isOver) colors.destructive else colors.mutedForeground,
            )
        }
        NemlaProgressBar(progress = usage.progress, modifier = Modifier.padding(top = 6.dp))
    }
}
