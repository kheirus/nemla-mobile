package app.nemla.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.theme.NemlaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> NemlaTabs(
    options: List<NemlaOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
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
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(selectedIndex, matchContentSize = false),
                color = colors.primary,
            )
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
