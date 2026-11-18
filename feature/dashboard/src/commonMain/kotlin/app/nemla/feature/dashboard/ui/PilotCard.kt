package app.nemla.feature.dashboard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaCard
import app.nemla.core.designsystem.component.NemlaCategoryDot
import app.nemla.core.designsystem.component.NemlaFormSheet
import app.nemla.core.designsystem.component.NemlaOption
import app.nemla.core.designsystem.component.NemlaProgressBar
import app.nemla.core.designsystem.component.NemlaSegmentedButtons
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.CategoryColors
import app.nemla.core.designsystem.theme.NemlaColors
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.PeriodKind
import app.nemla.feature.dashboard.DashboardAction
import app.nemla.feature.dashboard.DashboardUiState
import app.nemla.feature.dashboard.logic.FilterSummary
import app.nemla.feature.dashboard.logic.FilterView
import app.nemla.feature.dashboard.logic.PeriodPages
import app.nemla.feature.dashboard.logic.PilotView
import app.nemla.feature.dashboard.logic.Weather
import app.nemla.feature.dashboard.logic.WeatherView
import app.nemla.feature.dashboard.resources.Res
import app.nemla.feature.dashboard.resources.average_per_month
import app.nemla.feature.dashboard.resources.filter_all
import app.nemla.feature.dashboard.resources.filter_deselect_all
import app.nemla.feature.dashboard.resources.filter_empty
import app.nemla.feature.dashboard.resources.filter_none
import app.nemla.feature.dashboard.resources.filter_select_all
import app.nemla.feature.dashboard.resources.filter_some
import app.nemla.feature.dashboard.resources.filter_title
import app.nemla.feature.dashboard.resources.period_month
import app.nemla.feature.dashboard.resources.period_next_month
import app.nemla.feature.dashboard.resources.period_next_year
import app.nemla.feature.dashboard.resources.period_previous_month
import app.nemla.feature.dashboard.resources.period_previous_year
import app.nemla.feature.dashboard.resources.period_year
import app.nemla.feature.dashboard.resources.recurring_per_month
import app.nemla.feature.dashboard.resources.spent_month
import app.nemla.feature.dashboard.resources.spent_year
import app.nemla.feature.dashboard.resources.weather_cloud
import app.nemla.feature.dashboard.resources.weather_left
import app.nemla.feature.dashboard.resources.weather_of_month
import app.nemla.feature.dashboard.resources.weather_of_year
import app.nemla.feature.dashboard.resources.weather_over
import app.nemla.feature.dashboard.resources.weather_rain
import app.nemla.feature.dashboard.resources.weather_spent
import app.nemla.feature.dashboard.resources.weather_storm
import app.nemla.feature.dashboard.resources.weather_sun
import app.nemla.feature.dashboard.resources.weather_target
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val MUTED_PILL_ALPHA = 0.40f
private const val DISABLED_ARROW_ALPHA = 0.5f
private const val PLACEHOLDER_AMOUNT = "–"
private const val SELECTED_OPTION_ALPHA = 0.10f
private const val FILTER_SHEET_HEIGHT = 0.6f
private const val FILTER_SCRIM_ALPHA = 0.16f
private val PopoverWidth = 256.dp

@Composable
internal fun PilotCard(
    state: DashboardUiState,
    format: DashboardFormat,
    onAction: (DashboardAction) -> Unit,
) {
    val pilot = state.view.pilot
    val colors = NemlaTheme.colors
    NemlaCard(modifier = Modifier.fillMaxWidth(), contentPadding = NemlaTheme.spacing.md) {
        PeriodKindSelector(state.cursor.kind, onAction)
        PeriodPager(state, format, onAction)
        SectionDivider()
        MonthlyChartSection(
            chart = pilot.chart,
            animationKey = state.cursor.kind,
            format = format,
            onMonthTapped = { onAction(DashboardAction.ChartMonthTapped(it)) },
        )
        SectionDivider(top = NemlaTheme.spacing.md, bottom = NemlaTheme.spacing.sm)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterButton(pilot.filter, onAction)
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.average_per_month, format.money(pilot.averageCents)),
                style =
                    NemlaTheme.typography.caption
                        .copy(fontSize = 11.sp)
                        .tabular(),
                color = colors.mutedForeground,
            )
        }
    }
}

@Composable
private fun PeriodPager(
    state: DashboardUiState,
    format: DashboardFormat,
    onAction: (DashboardAction) -> Unit,
) {
    val cursor = state.cursor
    val pilot = state.view.pilot
    val targetPage = PeriodPages.pageOf(cursor)
    val pageCount = PeriodPages.count(cursor.kind, state.today)
    val currentTarget by rememberUpdatedState(targetPage)
    val currentOnAction by rememberUpdatedState(onAction)
    val spent = animatedCents(pilot.spentCents, animationKey = cursor.kind)
    val isYear = cursor.kind == PeriodKind.YEAR
    key(cursor.kind) {
        val shown = remember { mutableStateMapOf<Int, PilotView>() }
        if (!state.isPeriodLoading && pilot.kind == cursor.kind) shown[targetPage] = pilot
        val pagerState = rememberPagerState(initialPage = targetPage) { pageCount }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.targetPage }.collect { page ->
                if (page != currentTarget) currentOnAction(DashboardAction.PeriodPageSelected(page))
            }
        }
        LaunchedEffect(pagerState, targetPage) {
            if (pagerState.currentPage != targetPage && !pagerState.isScrollInProgress) pagerState.animateScrollToPage(targetPage)
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = NemlaTheme.spacing.xs)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                key = { it },
            ) { page ->
                val pageCursor = PeriodPages.cursorAt(page, cursor, state.today)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = NemlaTheme.sizes.minTouchTarget),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isYear) pageCursor.year.toString() else format.monthTitle(pageCursor.month),
                            style = NemlaTheme.typography.bodyEmphasis.tabular(),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                    SectionDivider()
                    val pagePilot = if (page == targetPage) pilot else shown[page]
                    if (pagePilot == null) {
                        PilotSummaryPlaceholder(cursor.kind)
                    } else {
                        PilotSummary(
                            pilot = pagePilot,
                            spentCents = if (page == targetPage) spent else pagePilot.spentCents,
                            isLoading = page == targetPage && state.isPeriodLoading,
                            state = state,
                            format = format,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = NemlaTheme.sizes.minTouchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PeriodArrow(
                    icon = NemlaIcons.ChevronLeft,
                    description = stringResource(if (isYear) Res.string.period_previous_year else Res.string.period_previous_month),
                    enabled = state.canGoPrevious,
                    onClick = { onAction(DashboardAction.PreviousPeriodClicked) },
                )
                Spacer(Modifier.weight(1f))
                PeriodArrow(
                    icon = NemlaIcons.ChevronRight,
                    description = stringResource(if (isYear) Res.string.period_next_year else Res.string.period_next_month),
                    enabled = state.canGoNext,
                    onClick = { onAction(DashboardAction.NextPeriodClicked) },
                )
            }
        }
    }
}

@Composable
private fun PilotSummary(
    pilot: PilotView,
    spentCents: Long,
    isLoading: Boolean,
    state: DashboardUiState,
    format: DashboardFormat,
) {
    val colors = NemlaTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm)) {
            Column(modifier = Modifier.weight(1f)) {
                SpentOverline(pilot.kind)
                Text(text = format.money(spentCents), style = NemlaTheme.typography.amount.tabular())
            }
            pilot.weather?.let { WeatherPill(it, state, format) }
        }
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = NemlaTheme.spacing.xs).heightIn(max = 2.dp),
                color = colors.primary,
                trackColor = Color.Transparent,
            )
        }
        pilot.progress?.let { NemlaProgressBar(progress = it, modifier = Modifier.padding(top = NemlaTheme.spacing.sm)) }
        if (pilot.monthlyRecurringCents > 0) {
            MutedCaption(
                text = stringResource(Res.string.recurring_per_month, format.money(pilot.monthlyRecurringCents)),
                modifier = Modifier.padding(top = NemlaTheme.spacing.xs),
            )
        }
    }
}

@Composable
private fun PilotSummaryPlaceholder(kind: PeriodKind) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SpentOverline(kind)
        Text(text = PLACEHOLDER_AMOUNT, style = NemlaTheme.typography.amount, color = NemlaTheme.colors.mutedForeground)
    }
}

@Composable
private fun SpentOverline(kind: PeriodKind) {
    Text(
        text = stringResource(if (kind == PeriodKind.YEAR) Res.string.spent_year else Res.string.spent_month).uppercase(),
        style = NemlaTheme.typography.overline,
        color = NemlaTheme.colors.mutedForeground,
    )
}

@Composable
private fun SectionDivider(
    top: Dp = NemlaTheme.spacing.md,
    bottom: Dp = NemlaTheme.spacing.md,
) {
    HorizontalDivider(
        modifier = Modifier.padding(top = top, bottom = bottom),
        color = NemlaTheme.colors.border.copy(alpha = NemlaTheme.colors.border.alpha * 0.7f),
    )
}

@Composable
private fun PeriodKindSelector(
    kind: PeriodKind,
    onAction: (DashboardAction) -> Unit,
) {
    NemlaSegmentedButtons(
        options =
            listOf(
                NemlaOption(PeriodKind.MONTH, stringResource(Res.string.period_month)),
                NemlaOption(PeriodKind.YEAR, stringResource(Res.string.period_year)),
            ),
        selected = kind,
        onSelect = { onAction(DashboardAction.PeriodKindSelected(it)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PeriodArrow(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = NemlaTheme.colors
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors =
            IconButtonDefaults.iconButtonColors(
                contentColor = colors.foreground,
                disabledContentColor = colors.mutedForeground.copy(alpha = DISABLED_ARROW_ALPHA),
            ),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

internal fun Weather.color(colors: NemlaColors): Color =
    when (this) {
        Weather.SUN -> colors.weatherSun
        Weather.CLOUD -> colors.weatherCloud
        Weather.RAIN -> colors.weatherRain
        Weather.STORM -> colors.weatherStorm
    }

internal fun Weather.icon(): ImageVector =
    when (this) {
        Weather.SUN -> NemlaIcons.Sun
        Weather.CLOUD -> NemlaIcons.Cloud
        Weather.RAIN -> NemlaIcons.CloudRain
        Weather.STORM -> NemlaIcons.CloudLightning
    }

internal fun Weather.label(): StringResource =
    when (this) {
        Weather.SUN -> Res.string.weather_sun
        Weather.CLOUD -> Res.string.weather_cloud
        Weather.RAIN -> Res.string.weather_rain
        Weather.STORM -> Res.string.weather_storm
    }

@Composable
private fun WeatherPill(
    weather: WeatherView,
    state: DashboardUiState,
    format: DashboardFormat,
) {
    var open by remember { mutableStateOf(false) }
    val colors = NemlaTheme.colors
    val tint = weather.weather.color(colors)
    Box {
        Surface(
            shape = CircleShape,
            color = colors.muted.copy(alpha = MUTED_PILL_ALPHA).compositeOver(colors.card),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.clip(CircleShape).clickable(role = Role.Button) { open = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(weather.weather.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(weather.weather.label()),
                    style = NemlaTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = tint,
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(NemlaTheme.radius.md),
            containerColor = colors.popover,
            modifier = Modifier.width(PopoverWidth),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = NemlaTheme.spacing.sm, vertical = NemlaTheme.spacing.xxs),
                verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
            ) {
                MutedCaption(
                    if (weather.kind == PeriodKind.YEAR) {
                        stringResource(
                            Res.string.weather_of_year,
                            state.view.pilot.chart.year
                                .toString(),
                        )
                    } else {
                        stringResource(Res.string.weather_of_month, format.monthTitle(state.cursor.month))
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(weather.weather.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    Text(stringResource(weather.weather.label()), style = NemlaTheme.typography.bodyEmphasis, color = tint)
                }
                NemlaProgressBar(progress = weather.progress)
                MutedCaption(stringResource(Res.string.weather_spent, format.money(weather.spentCents)))
                MutedCaption(stringResource(Res.string.weather_target, format.money(weather.budgetCents)))
                MutedCaption(
                    if (weather.remainingCents >= 0) {
                        stringResource(Res.string.weather_left, format.money(weather.remainingCents), format.money(weather.budgetCents))
                    } else {
                        stringResource(Res.string.weather_over, format.money(-weather.remainingCents), format.money(weather.budgetCents))
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterButton(
    filter: FilterView,
    onAction: (DashboardAction) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val colors = NemlaTheme.colors
    val label =
        when (filter.summary) {
            FilterSummary.ALL -> stringResource(Res.string.filter_all)
            FilterSummary.NONE -> stringResource(Res.string.filter_none)
            FilterSummary.SOME -> pluralStringResource(Res.plurals.filter_some, filter.selectedCount, filter.selectedCount)
        }
    NemlaButton(text = label, onClick = { open = true }, variant = NemlaButtonVariant.Outline, leadingIcon = NemlaIcons.Filter)
    if (open) {
        NemlaFormSheet(
            title = stringResource(Res.string.filter_title),
            onDismissRequest = { open = false },
            maxHeightFraction = FILTER_SHEET_HEIGHT,
            scrimColor = Color.Black.copy(alpha = FILTER_SCRIM_ALPHA),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1f))
                NemlaButton(
                    text =
                        stringResource(
                            if (filter.summary == FilterSummary.ALL) Res.string.filter_deselect_all else Res.string.filter_select_all,
                        ),
                    onClick = { onAction(DashboardAction.FilterAllToggled) },
                    variant = NemlaButtonVariant.Ghost,
                )
            }
            if (filter.summary == FilterSummary.NONE) {
                Text(
                    text = stringResource(Res.string.filter_empty),
                    style = NemlaTheme.typography.caption,
                    color = colors.destructive,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(colors.destructive.copy(alpha = 0.10f), RoundedCornerShape(NemlaTheme.radius.sm))
                            .padding(NemlaTheme.spacing.xs),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
                filter.options.forEach { option ->
                    val selected = option.selected && filter.summary != FilterSummary.NONE
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = NemlaTheme.sizes.minTouchTarget)
                                .clip(RoundedCornerShape(NemlaTheme.radius.md))
                                .background(if (selected) colors.primary.copy(alpha = SELECTED_OPTION_ALPHA) else Color.Transparent)
                                .clickable(role = Role.Checkbox) { onAction(DashboardAction.FilterToggled(option.id)) }
                                .padding(horizontal = NemlaTheme.spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NemlaCategoryDot(CategoryColors.parse(option.colorHex), Modifier.size(16.dp))
                        Text(
                            text = option.name,
                            style = if (selected) NemlaTheme.typography.bodyStrong else NemlaTheme.typography.body,
                            color = if (selected) colors.primary else colors.foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(NemlaIcons.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
