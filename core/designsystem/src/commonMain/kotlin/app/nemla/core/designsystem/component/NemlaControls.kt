package app.nemla.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.brand.NemlaLogo
import app.nemla.core.designsystem.brand.NemlaLogoSize
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme

private const val AVATAR_ALPHA = 0.15f
private const val CHEVRON_ALPHA = 0.6f
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
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = NemlaTheme.colors
    val shape = RoundedCornerShape(NemlaTheme.radius.md)
    BasicTextField(
        value = value,
        onValueChange = { next -> onValueChange(if (maxLength != null) next.take(maxLength) else next) },
        enabled = enabled,
        minLines = minLines,
        textStyle = NemlaTheme.typography.input.copy(color = colors.foreground),
        cursorBrush = SolidColor(colors.primary),
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
        decorationBox = { inner ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .border(if (focused) 2.dp else 1.dp, if (focused) colors.ring else colors.input, shape)
                        .padding(NemlaTheme.spacing.sm),
            ) {
                if (value.isEmpty() && placeholder != null) {
                    Text(
                        text = placeholder,
                        style = NemlaTheme.typography.input,
                        color = colors.mutedForeground,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
                inner()
            }
        },
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

@Composable
fun NemlaAppHeader(
    accountLabel: String,
    avatar: ImageVector,
    menuDescription: String,
    modifier: Modifier = Modifier,
    menu: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val colors = NemlaTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NemlaTheme.spacing.pageHorizontal, vertical = NemlaTheme.spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NemlaLogo(size = NemlaLogoSize.Header)
            Box {
                Surface(
                    shape = CircleShape,
                    color = colors.secondary,
                    contentColor = colors.secondaryForeground,
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { menuOpen = true }
                            .semantics { contentDescription = menuDescription },
                ) {
                    Row(
                        modifier = Modifier.padding(start = 4.dp, end = NemlaTheme.spacing.sm, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(28.dp)
                                    .background(colors.accent.copy(alpha = AVATAR_ALPHA).compositeOver(colors.secondary), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(avatar, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = accountLabel,
                            style = NemlaTheme.typography.bodyStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 96.dp),
                        )
                        Icon(
                            NemlaIcons.ChevronDown,
                            contentDescription = null,
                            tint = colors.secondaryForeground.copy(alpha = CHEVRON_ALPHA),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(NemlaTheme.radius.md),
                    containerColor = colors.popover,
                    modifier = Modifier.widthIn(min = 224.dp),
                ) {
                    Text(
                        text = accountLabel,
                        style = NemlaTheme.typography.bodyStrong,
                        modifier = Modifier.padding(horizontal = NemlaTheme.spacing.md, vertical = NemlaTheme.spacing.xs),
                    )
                    HorizontalDivider(color = colors.border)
                    menu { menuOpen = false }
                }
            }
        }
        HorizontalDivider(color = colors.border)
    }
}

@Composable
fun NemlaMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = { Text(text, style = NemlaTheme.typography.body) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun NemlaEntryButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val colors = NemlaTheme.colors
    val shape = RoundedCornerShape(NemlaTheme.radius.xl)
    Surface(
        onClick = onClick,
        shape = shape,
        color = colors.card,
        contentColor = colors.cardForeground,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(NemlaTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .background(colors.primary.copy(alpha = SELECTED_ALPHA).compositeOver(colors.card), CircleShape)
                        .padding(NemlaTheme.spacing.xs),
            ) {
                Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(NemlaTheme.sizes.iconLarge))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = NemlaTheme.typography.bodyStrong)
                Text(subtitle, style = NemlaTheme.typography.caption, color = colors.mutedForeground)
                if (detail != null) {
                    Text(detail, style = NemlaTheme.typography.caption.copy(fontFeatureSettings = "tnum"), color = colors.foreground)
                }
            }
            Icon(
                NemlaIcons.ChevronRight,
                contentDescription = null,
                tint = colors.mutedForeground,
                modifier = Modifier.size(NemlaTheme.sizes.iconLarge),
            )
        }
    }
}
