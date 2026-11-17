package app.nemla.feature.dashboard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.component.NemlaCard
import app.nemla.core.designsystem.component.NemlaCategoryDot
import app.nemla.core.designsystem.component.NemlaOption
import app.nemla.core.designsystem.component.NemlaProgressBar
import app.nemla.core.designsystem.component.NemlaSegmentedControl
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
import app.nemla.feature.dashboard.resources.spending_title
import app.nemla.feature.dashboard.resources.tab_breakdown
import app.nemla.feature.dashboard.resources.tab_budgets
import org.jetbrains.compose.resources.stringResource

private val DonutHeight = 192.dp
private const val INNER_RADIUS = 0.58f
private const val OUTER_RADIUS = 0.88f
private const val FULL_TURN = 360f

@Composable
internal fun SpendingCard(
    state: DashboardUiState,
    format: DashboardFormat,
    onAction: (DashboardAction) -> Unit,
) {
    val spending = state.view.spending
    val tab = state.effectiveSpendingTab
    NemlaCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm)) {
            Box(modifier = Modifier.weight(1f)) {
                CardTitle(stringResource(if (tab == SpendingTab.BUDGETS) Res.string.budgets_title else Res.string.spending_title))
            }
            if (spending.hasBudgets) {
                NemlaSegmentedControl(
                    options =
                        listOf(
                            NemlaOption(SpendingTab.BREAKDOWN, stringResource(Res.string.tab_breakdown)),
                            NemlaOption(SpendingTab.BUDGETS, stringResource(Res.string.tab_budgets)),
                        ),
                    selected = tab,
                    onSelect = { onAction(DashboardAction.SpendingTabSelected(it)) },
                    modifier = Modifier.widthIn(max = 220.dp),
                )
            }
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
    Donut(groups)
    groups.forEach { group ->
        GroupRow(group, group.key == expanded, format, onAction)
    }
}

@Composable
private fun Donut(groups: List<SpendingGroup>) {
    val description = stringResource(Res.string.spending_chart_description)
    val positive = groups.filter { it.totalCents > 0 }
    val total = positive.sumOf { it.totalCents }
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(sweep) { sweep.animateTo(FULL_TURN, tween(DONUT_SWEEP_MILLIS, easing = FastOutSlowInEasing)) }
    Canvas(modifier = Modifier.fillMaxWidth().height(DonutHeight).semantics { contentDescription = description }) {
        if (total <= 0) return@Canvas
        val half = minOf(size.width, size.height) / 2f
        val outer = half * OUTER_RADIUS
        val inner = half * INNER_RADIUS
        val ringRadius = (outer + inner) / 2f
        val thickness = outer - inner
        val topLeft = Offset(center.x - ringRadius, center.y - ringRadius)
        val limit = sweep.value
        var start = 0f
        positive.forEach { group ->
            val full = (group.totalCents.toDouble() / total.toDouble() * FULL_TURN).toFloat()
            val visible = minOf(full, limit + start).coerceAtLeast(0f)
            if (visible <= 0f) return@forEach
            drawArc(
                color = group.colorOrOther(),
                startAngle = start,
                sweepAngle = -visible,
                useCenter = false,
                topLeft = topLeft,
                size = Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(width = thickness),
            )
            start -= full
        }
    }
}

private fun SpendingGroup.colorOrOther() = if (isOther || colorHex == null) CategoryColors.OtherGroup else CategoryColors.parse(colorHex)

@Composable
private fun GroupRow(
    group: SpendingGroup,
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
