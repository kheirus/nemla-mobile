package app.nemla.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.theme.NemlaTheme

@Composable
fun <T> NemlaSegmentedControl(
    options: List<NemlaOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelMaxLines: Int = 1,
) {
    val colors = NemlaTheme.colors
    val shape = RoundedCornerShape(NemlaTheme.radius.lg)
    Row(
        modifier =
            modifier
                .clip(shape)
                .background(colors.muted)
                .padding(4.dp)
                .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option -> Segment(option, option.value == selected, labelMaxLines, onSelect) }
    }
}

@Composable
private fun <T> RowScope.Segment(
    option: NemlaOption<T>,
    isSelected: Boolean,
    labelMaxLines: Int,
    onSelect: (T) -> Unit,
) {
    val colors = NemlaTheme.colors
    val shape = RoundedCornerShape(NemlaTheme.radius.md)
    Box(
        modifier =
            Modifier
                .weight(1f)
                .heightIn(min = SEGMENT_HEIGHT)
                .then(if (isSelected) Modifier.shadow(1.dp, shape) else Modifier)
                .clip(shape)
                .background(if (isSelected) colors.background else Color.Transparent)
                .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option.value) })
                .padding(horizontal = NemlaTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = option.label,
            style = NemlaTheme.typography.button,
            color = if (isSelected) colors.foreground else colors.mutedForeground,
            maxLines = labelMaxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private val SEGMENT_HEIGHT = 36.dp

@Composable
fun NemlaCategoryDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clip(CircleShape).background(color))
}
