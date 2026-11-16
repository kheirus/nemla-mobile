package app.nemla.core.designsystem.component

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme

@Immutable
data class NemlaOption<T>(
    val value: T,
    val label: String,
)

@Composable
fun <T> NemlaSelectField(
    label: String,
    placeholder: String,
    options: List<NemlaOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = NemlaTheme.colors
    val shape = RoundedCornerShape(NemlaTheme.radius.md)
    val selectedLabel = options.firstOrNull { it.value == selected }?.label
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.labelGap),
    ) {
        Text(
            text = label,
            style = NemlaTheme.typography.label,
            color = colors.foreground,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Box {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = NemlaTheme.sizes.controlHeight)
                        .clip(shape)
                        .border(1.dp, if (expanded) colors.ring else colors.input, shape)
                        .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true }
                        .semantics {
                            contentDescription = label
                            stateDescription = selectedLabel ?: placeholder
                        }.padding(horizontal = NemlaTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedLabel ?: placeholder,
                    style = NemlaTheme.typography.input,
                    color = if (selectedLabel == null) colors.mutedForeground else colors.foreground,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                )
                Icon(
                    imageVector = NemlaIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.foreground.copy(alpha = 0.5f),
                    modifier = Modifier.size(NemlaTheme.sizes.icon),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = shape,
                containerColor = colors.popover,
            ) {
                options.forEach { option ->
                    val isSelected = option.value == selected
                    DropdownMenuItem(
                        text = { Text(text = option.label, style = NemlaTheme.typography.body) },
                        onClick = {
                            expanded = false
                            onSelect(option.value)
                        },
                        trailingIcon =
                            if (isSelected) {
                                {
                                    Icon(
                                        imageVector = NemlaIcons.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(NemlaTheme.sizes.icon),
                                    )
                                }
                            } else {
                                null
                            },
                        colors =
                            MenuDefaults.itemColors(
                                textColor = colors.popoverForeground,
                                trailingIconColor = colors.popoverForeground,
                            ),
                        modifier =
                            Modifier
                                .heightIn(min = NemlaTheme.sizes.minTouchTarget)
                                .semantics {
                                    role = Role.RadioButton
                                    this.selected = isSelected
                                },
                    )
                }
            }
        }
    }
}
