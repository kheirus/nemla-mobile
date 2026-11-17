package app.nemla.feature.dashboard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.feature.dashboard.logic.ChartBar
import app.nemla.feature.dashboard.logic.ChartView
import app.nemla.feature.dashboard.resources.Res
import app.nemla.feature.dashboard.resources.chart_description
import app.nemla.feature.dashboard.resources.chart_hint
import app.nemla.feature.dashboard.resources.chart_legend_budget
import app.nemla.feature.dashboard.resources.chart_legend_planned
import app.nemla.feature.dashboard.resources.chart_legend_spent
import app.nemla.feature.dashboard.resources.chart_target
import app.nemla.feature.dashboard.resources.chart_title
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val ChartHeight = 208.dp
private val AxisWidth = 46.dp
private val MaxBarWidth = 28.dp
private val BarRadius = 5.dp
private val XLabelHeight = 18.dp
private val TopPadding = 8.dp
private val SidePadding = 8.dp
private val BottomPadding = 4.dp
private const val WIDE_LAYOUT_DP = 640
private const val BAR_FILL = 0.7f
private const val GRID_ALPHA = 0.6f
private const val UNSELECTED_ALPHA = 0.38f
private const val UNSELECTED_ALPHA_DARK = 0.72f
private const val PLANNED_ALPHA = 0.18f
private const val PLANNED_STROKE_ALPHA = 0.40f
private const val CURSOR_ALPHA = 0.40f
private const val LEGEND_SPENT_ALPHA = 0.35f
private const val LEGEND_SPENT_ALPHA_DARK = 0.72f
private const val LAST_BAR_INDEX = 11

@Composable
internal fun MonthlyChartSection(
    chart: ChartView,
    format: DashboardFormat,
    onMonthTapped: (Int) -> Unit,
) {
    val colors = NemlaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
        Text(
            text = stringResource(Res.string.chart_title, chart.year.toString()),
            style = NemlaTheme.typography.subTitle,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(
                colors.primary.copy(alpha = if (colors.isDark) LEGEND_SPENT_ALPHA_DARK else LEGEND_SPENT_ALPHA),
                stringResource(Res.string.chart_legend_spent),
            )
            if (chart.showsPlanned) {
                LegendDot(colors.primary.copy(alpha = PLANNED_ALPHA), stringResource(Res.string.chart_legend_planned))
            }
            if (chart.budgetLineCents != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(16.dp).height(2.dp).background(colors.destructive))
                    LegendText(stringResource(Res.string.chart_legend_budget))
                }
            }
        }
        MonthlyChart(chart = chart, format = format, onMonthTapped = onMonthTapped)
        Text(
            text = stringResource(Res.string.chart_hint),
            style = NemlaTheme.typography.caption.copy(fontSize = 11.sp),
            color = colors.mutedForeground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LegendDot(
    color: Color,
    label: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        LegendText(label)
    }
}

@Composable
private fun LegendText(text: String) {
    Text(text, style = NemlaTheme.typography.caption.copy(fontSize = 11.sp), color = NemlaTheme.colors.mutedForeground)
}

@Composable
private fun MonthlyChart(
    chart: ChartView,
    format: DashboardFormat,
    onMonthTapped: (Int) -> Unit,
) {
    val colors = NemlaTheme.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val currentOnTap by rememberUpdatedState(onMonthTapped)
    var pressed by remember { mutableStateOf<Int?>(null) }
    var tooltipWidth by remember { mutableIntStateOf(0) }
    val description = stringResource(Res.string.chart_description)
    val targetLabel = chart.budgetLineCents?.let { stringResource(Res.string.chart_target, format.money(it)) }
    val axisStyle = TextStyle(fontSize = 10.sp, color = colors.mutedForeground)
    val monthStyle = TextStyle(fontSize = 11.sp, color = colors.mutedForeground)
    val targetStyle = TextStyle(fontSize = 10.sp, color = colors.destructive)
    val unselectedAlpha = if (colors.isDark) UNSELECTED_ALPHA_DARK else UNSELECTED_ALPHA
    val riseMillis = (BAR_RISE_MILLIS + LAST_BAR_INDEX * BAR_STAGGER_MILLIS).toFloat()
    val rise = remember(chart.year) { Animatable(0f) }
    LaunchedEffect(rise) { rise.animateTo(riseMillis, tween(riseMillis.toInt(), easing = LinearEasing)) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(ChartHeight)) {
        val wide = maxWidth.value >= WIDE_LAYOUT_DP
        val widthPx = with(density) { maxWidth.toPx() }
        val geometry = with(density) { ChartGeometry(widthPx, ChartHeight.toPx(), this) }
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(ChartHeight)
                    .semantics { contentDescription = description }
                    .pointerInput(chart.year, widthPx) {
                        detectTapGestures(
                            onPress = { offset ->
                                pressed = geometry.slotAt(offset.x)
                                tryAwaitRelease()
                                pressed = null
                            },
                            onTap = { offset -> geometry.slotAt(offset.x)?.let { currentOnTap(it + 1) } },
                        )
                    },
        ) {
            pressed?.let { slot ->
                drawRect(
                    color = colors.muted.copy(alpha = CURSOR_ALPHA),
                    topLeft = Offset(geometry.slotLeft(slot), geometry.plotTop),
                    size = Size(geometry.slotWidth, geometry.plotHeight),
                )
            }
            chart.ticks.forEach { tick ->
                val y = geometry.yOf(tick, chart.axisMaxCents)
                drawLine(
                    color = colors.border.copy(alpha = colors.border.alpha * GRID_ALPHA),
                    start = Offset(geometry.plotLeft, y),
                    end = Offset(geometry.plotRight, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
                val layout = measurer.measure(format.compact(tick), axisStyle)
                drawText(
                    layout,
                    topLeft =
                        Offset(
                            geometry.plotLeft - layout.size.width - 4.dp.toPx(),
                            (y - layout.size.height / 2f).coerceAtLeast(0f),
                        ),
                )
            }
            chart.bars.forEachIndexed { index, bar ->
                drawBar(
                    index = index,
                    bar = bar,
                    axisMax = chart.axisMaxCents,
                    geometry = geometry,
                    primary = colors.primary,
                    unselectedAlpha = unselectedAlpha,
                    growth = staggeredFraction(rise.value, index),
                )
            }
            val budget = chart.budgetLineCents
            if (budget != null && targetLabel != null) {
                val y = geometry.yOf(budget, chart.axisMaxCents)
                drawLine(
                    color = colors.destructive,
                    start = Offset(geometry.plotLeft, y),
                    end = Offset(geometry.plotRight, y),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                )
                val layout = measurer.measure(targetLabel, targetStyle)
                drawText(
                    layout,
                    topLeft = Offset(geometry.plotRight - layout.size.width, (y - layout.size.height - 2.dp.toPx()).coerceAtLeast(0f)),
                )
            }
            chart.bars.forEachIndexed { index, bar ->
                val text = if (wide) format.monthShort(bar.monthNumber) else format.monthInitial(bar.monthNumber)
                val layout = measurer.measure(text, monthStyle)
                drawText(
                    layout,
                    topLeft = Offset(geometry.slotCenter(index) - layout.size.width / 2f, geometry.plotBottom + 2.dp.toPx()),
                )
            }
        }
        pressed?.let { slot ->
            val bar = chart.bars[slot]
            val left = (geometry.slotCenter(slot) - tooltipWidth / 2f).coerceIn(0f, (widthPx - tooltipWidth).coerceAtLeast(0f))
            Text(
                text = format.monthShort(bar.monthNumber) + " " + format.money(bar.stackedCents),
                style =
                    NemlaTheme.typography.caption
                        .copy(fontWeight = FontWeight.Medium)
                        .tabular(),
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(left.roundToInt(), 0) }
                        .onSizeChanged { tooltipWidth = it.width }
                        .background(colors.card.copy(alpha = 0.95f), RoundedCornerShape(percent = 50))
                        .border(1.dp, colors.border, RoundedCornerShape(percent = 50))
                        .padding(horizontal = NemlaTheme.spacing.sm, vertical = NemlaTheme.spacing.xxs),
            )
        }
    }
}

private class ChartGeometry(
    width: Float,
    height: Float,
    density: Density,
) {
    val plotLeft: Float
    val plotRight: Float
    val plotTop: Float
    val plotBottom: Float
    val slotWidth: Float
    val barWidth: Float
    val radius: Float

    init {
        with(density) {
            plotLeft = SidePadding.toPx() + AxisWidth.toPx()
            plotRight = width - SidePadding.toPx()
            plotTop = TopPadding.toPx()
            plotBottom = height - BottomPadding.toPx() - XLabelHeight.toPx()
            slotWidth = ((plotRight - plotLeft) / MONTHS).coerceAtLeast(1f)
            barWidth = minOf(MaxBarWidth.toPx(), slotWidth * BAR_FILL)
            radius = BarRadius.toPx()
        }
    }

    val plotHeight: Float get() = plotBottom - plotTop

    fun slotLeft(index: Int): Float = plotLeft + index * slotWidth

    fun slotCenter(index: Int): Float = slotLeft(index) + slotWidth / 2f

    fun slotAt(x: Float): Int? {
        if (x < plotLeft || x > plotRight) return null
        return ((x - plotLeft) / slotWidth).toInt().coerceIn(0, MONTHS - 1)
    }

    fun yOf(
        cents: Long,
        axisMax: Long,
    ): Float {
        if (axisMax <= 0) return plotBottom
        val fraction = (cents.toDouble() / axisMax.toDouble()).toFloat().coerceIn(0f, 1f)
        return plotBottom - fraction * plotHeight
    }

    companion object {
        const val MONTHS = 12
    }
}

private fun DrawScope.drawBar(
    index: Int,
    bar: ChartBar,
    axisMax: Long,
    geometry: ChartGeometry,
    primary: Color,
    unselectedAlpha: Float,
    growth: Float,
) {
    if (growth <= 0f) return
    val left = geometry.slotCenter(index) - geometry.barWidth / 2f
    val bottom = geometry.plotBottom
    val totalTop = bottom - (bottom - geometry.yOf(bar.totalCents.coerceAtLeast(0), axisMax)) * growth
    if (bar.totalCents > 0) {
        val path = topRoundedBar(left, totalTop, geometry.barWidth, bottom - totalTop, geometry.radius)
        drawPath(path, color = if (bar.selected) primary else primary.copy(alpha = unselectedAlpha))
        if (bar.selected) drawPath(path, color = primary, style = Stroke(width = 2.dp.toPx()))
    }
    if (bar.plannedCents > 0) {
        val plannedTop = bottom - (bottom - geometry.yOf(bar.stackedCents.coerceAtLeast(0), axisMax)) * growth
        val path = topRoundedBar(left, plannedTop, geometry.barWidth, totalTop - plannedTop, geometry.radius)
        drawPath(path, color = primary.copy(alpha = PLANNED_ALPHA))
        drawPath(
            path,
            color = primary.copy(alpha = PLANNED_STROKE_ALPHA),
            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
        )
    }
}

private fun topRoundedBar(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    radius: Float,
): Path {
    val corner = CornerRadius(minOf(radius, width / 2f, height.coerceAtLeast(0f)))
    return Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = left + width,
                bottom = top + height.coerceAtLeast(0f),
                topLeftCornerRadius = corner,
                topRightCornerRadius = corner,
                bottomRightCornerRadius = CornerRadius.Zero,
                bottomLeftCornerRadius = CornerRadius.Zero,
            ),
        )
    }
}
