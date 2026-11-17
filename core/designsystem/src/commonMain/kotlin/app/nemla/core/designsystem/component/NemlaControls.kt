package app.nemla.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.theme.NemlaTheme

private const val SELECTED_ALPHA = 0.10f
private const val DISABLED_ALPHA = 0.5f

@Composable
fun NemlaTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    minLines: Int = 3,
    enabled: Boolean = true,
    maxLength: Int? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(if (maxLength != null) next.take(maxLength) else next) },
        enabled = enabled,
        minLines = minLines,
        textStyle = NemlaTheme.typography.input,
        placeholder = placeholder?.let { text -> { Text(text, style = NemlaTheme.typography.input) } },
        shape = RoundedCornerShape(NemlaTheme.radius.md),
        colors = nemlaOutlinedFieldColors(),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
    )
}

@Composable
fun NemlaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = NemlaTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = colors.background,
                checkedTrackColor = colors.primary,
                checkedBorderColor = colors.primary,
                uncheckedThumbColor = colors.background,
                uncheckedTrackColor = colors.input,
                uncheckedBorderColor = colors.input,
            ),
        thumbContent = null,
    )
}

@Composable
fun NemlaChoiceCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
) {
    val colors = NemlaTheme.colors
    val shape = RoundedCornerShape(NemlaTheme.radius.xl)
    Column(
        modifier =
            modifier
                .clip(shape)
                .border(1.dp, if (selected) colors.primary else colors.border, shape)
                .background(if (selected) colors.primary.copy(alpha = SELECTED_ALPHA).compositeOver(colors.card) else colors.card)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(NemlaTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) colors.primary else colors.foreground,
                    modifier = Modifier.size(NemlaTheme.sizes.icon),
                )
            }
            Text(title, style = NemlaTheme.typography.bodyStrong, color = if (selected) colors.primary else colors.foreground)
        }
        if (description != null) {
            Text(description, style = NemlaTheme.typography.caption, color = colors.mutedForeground)
        }
    }
}

@Composable
fun NemlaIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = NemlaTheme.colors.foreground,
    size: Dp = NemlaTheme.sizes.minTouchTarget,
    bordered: Boolean = false,
) {
    val colors = NemlaTheme.colors
    val shape = RoundedCornerShape(NemlaTheme.radius.md)
    Box(
        modifier =
            modifier
                .size(size)
                .clip(shape)
                .then(if (bordered) Modifier.border(1.dp, colors.input, shape) else Modifier)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = DISABLED_ALPHA),
            modifier = Modifier.size(NemlaTheme.sizes.icon),
        )
    }
}

@Composable
fun NemlaChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = NemlaTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = colors.secondary,
        contentColor = colors.secondaryForeground,
        modifier = modifier.heightIn(min = 28.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = NemlaTheme.spacing.sm, vertical = 4.dp)) {
            Text(text, style = NemlaTheme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
