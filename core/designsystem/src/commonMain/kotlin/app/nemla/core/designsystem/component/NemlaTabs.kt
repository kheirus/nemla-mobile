package app.nemla.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.nemla.core.designsystem.theme.NemlaTheme
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> NemlaTabs(
    options: List<NemlaOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    indicatorPosition: (() -> Float)? = null,
) {
    val colors = NemlaTheme.colors
    val selectedIndex = options.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    SecondaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = colors.foreground,
        edgePadding = 0.dp,
        indicator = {
            val indicatorModifier =
                if (indicatorPosition == null) {
                    Modifier.tabIndicatorOffset(selectedIndex, matchContentSize = false)
                } else {
                    Modifier.tabIndicatorLayout { measurable, constraints, positions ->
                        followPosition(measurable, constraints, positions, selectedIndex, indicatorPosition())
                    }
                }
            TabRowDefaults.SecondaryIndicator(modifier = indicatorModifier, color = colors.primary)
        },
        divider = { HorizontalDivider(color = colors.border) },
    ) {
        options.forEachIndexed { index, option ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(option.value) },
                text = { Text(option.label, style = NemlaTheme.typography.button, maxLines = 1) },
                selectedContentColor = colors.primary,
                unselectedContentColor = colors.mutedForeground,
            )
        }
    }
}

private fun MeasureScope.followPosition(
    measurable: Measurable,
    constraints: Constraints,
    positions: List<TabPosition>,
    selectedIndex: Int,
    position: Float,
): MeasureResult {
    if (positions.isEmpty()) return layout(0, 0) {}
    val last = positions.lastIndex
    val clamped = position.coerceIn(0f, last.toFloat())
    val start = floor(clamped).toInt().coerceIn(0, last)
    val end = (start + 1).coerceAtMost(last)
    val fraction = clamped - start
    val left: Dp = lerp(positions[start].left, positions[end].left, fraction)
    val width: Dp = lerp(positions[start].width, positions[end].width, fraction)
    val widthPx = width.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = widthPx, maxWidth = widthPx))
    val selectedWidthPx = positions[selectedIndex.coerceIn(0, last)].width.roundToPx()
    val offset = left.roundToPx() - (selectedWidthPx - widthPx) / 2
    return layout(placeable.width, placeable.height) { placeable.place(offset, 0) }
}
