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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaCard
import app.nemla.core.designsystem.component.NemlaCategoryDot
import app.nemla.core.designsystem.component.NemlaOption
import app.nemla.core.designsystem.component.NemlaProgressBar
import app.nemla.core.designsystem.component.NemlaSegmentedControl
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.CategoryColors
import app.nemla.core.designsystem.theme.NemlaColors
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.PeriodKind
import app.nemla.feature.dashboard.DashboardAction
import app.nemla.feature.dashboard.DashboardUiState
import app.nemla.feature.dashboard.logic.FilterSummary
import app.nemla.feature.dashboard.logic.FilterView
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
private const val SELECTED_OPTION_ALPHA = 0.10f
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
        PeriodRow(state, format, onAction)
        SectionDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (pilot.kind == PeriodKind.YEAR) Res.string.spent_year else Res.string.spent_month).uppercase(),
                    style = NemlaTheme.typography.overline,
                    color = colors.mutedForeground,
                )
                Text(text = format.money(pilot.spentCents), style = NemlaTheme.typography.amount)
            }
            pilot.weather?.let { WeatherPill(it, state, format) }
        }
        if (state.isPeriodLoading) {
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
        SectionDivider()
        MonthlyChartSection(
            chart = pilot.chart,
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
private fun SectionDivider(
    top: androidx.compose.ui.unit.Dp = NemlaTheme.spacing.md,
    bottom: androidx.compose.ui.unit.Dp = NemlaTheme.spacing.md,
) {
    HorizontalDivider(
        modifier = Modifier.padding(top = top, bottom = bottom),
        color = NemlaTheme.colors.border.copy(alpha = NemlaTheme.colors.border.alpha * 0.7f),
    )
}

@Composable
private fun PeriodRow(
    state: DashboardUiState,
    format: DashboardFormat,
    onAction: (DashboardAction) -> Unit,
) {
    val cursor = state.cursor
    val isYear = cursor.kind == PeriodKind.YEAR
    val colors = NemlaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm)) {
        NemlaSegmentedControl(
            options =
                listOf(
                    NemlaOption(PeriodKind.MONTH, stringResource(Res.string.period_month)),
                    NemlaOption(PeriodKind.YEAR, stringResource(Res.string.period_year)),
                ),
            selected = cursor.kind,
            onSelect = { onAction(DashboardAction.PeriodKindSelected(it)) },
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(colors.muted.copy(alpha = MUTED_PILL_ALPHA))
                    .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RoundArrow(
                icon = NemlaIcons.ChevronLeft,
                description = stringResource(if (isYear) Res.string.period_previous_year else Res.string.period_previous_month),
                onClick = { onAction(DashboardAction.PreviousPeriodClicked) },
            )
            Text(
                text = if (isYear) cursor.year.toString() else format.monthTitle(cursor.month),
                style = NemlaTheme.typography.bodyEmphasis.tabular(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 96.dp),
            )
            RoundArrow(
                icon = NemlaIcons.ChevronRight,
                description = stringResource(if (isYear) Res.string.period_next_year else Res.string.period_next_month),
                onClick = { onAction(DashboardAction.NextPeriodClicked) },
            )
        }
    }
}

@Composable
private fun RoundArrow(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val colors = NemlaTheme.colors
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = colors.secondary, contentColor = colors.secondaryForeground),
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
    Box {
        NemlaButton(text = label, onClick = { open = true }, variant = NemlaButtonVariant.Outline, leadingIcon = NemlaIcons.Filter)
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(NemlaTheme.radius.md),
            containerColor = colors.popover,
            modifier = Modifier.width(PopoverWidth),
        ) {
            Column(modifier = Modifier.padding(horizontal = NemlaTheme.spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.filter_title),
                        style = NemlaTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                        color = colors.mutedForeground,
                        modifier = Modifier.weight(1f),
                    )
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
                                .padding(vertical = NemlaTheme.spacing.xxs)
                                .background(colors.destructive.copy(alpha = 0.10f), RoundedCornerShape(NemlaTheme.radius.sm))
                                .padding(NemlaTheme.spacing.xs),
                    )
                }
                Column(modifier = Modifier.heightIn(max = 256.dp).verticalScroll(rememberScrollState())) {
                    filter.options.forEach { option ->
                        val selected = option.selected && filter.summary != FilterSummary.NONE
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = NemlaTheme.sizes.minTouchTarget)
                                    .clip(RoundedCornerShape(NemlaTheme.radius.sm))
                                    .background(if (selected) colors.primary.copy(alpha = SELECTED_OPTION_ALPHA) else Color.Transparent)
                                    .clickable(role = Role.Checkbox) { onAction(DashboardAction.FilterToggled(option.id)) }
                                    .padding(horizontal = NemlaTheme.spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NemlaCategoryDot(CategoryColors.parse(option.colorHex), Modifier.size(14.dp))
                            Text(
                                text = option.name,
                                style = if (selected) NemlaTheme.typography.bodyStrong else NemlaTheme.typography.body,
                                color = if (selected) colors.primary else colors.foreground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(
                                    NemlaIcons.Check,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
