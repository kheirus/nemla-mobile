package app.nemla.core.designsystem.component

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.nemla.core.designsystem.theme.NemlaTheme

data class NemlaNavigationItem<T>(
    val key: T,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun <T> NemlaNavigationBar(
    items: List<NemlaNavigationItem<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NemlaTheme.colors
    val scheme = MaterialTheme.colorScheme
    NavigationBar(modifier = modifier, containerColor = scheme.surfaceContainer, contentColor = colors.foreground) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.key == selected,
                onClick = { onSelect(item.key) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label, style = NemlaTheme.typography.buttonSmall, maxLines = 1) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = scheme.onSecondaryContainer,
                        selectedTextColor = colors.foreground,
                        indicatorColor = scheme.secondaryContainer,
                        unselectedIconColor = colors.mutedForeground,
                        unselectedTextColor = colors.mutedForeground,
                    ),
            )
        }
    }
}
