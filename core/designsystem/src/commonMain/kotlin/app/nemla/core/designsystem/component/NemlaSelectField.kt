package app.nemla.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> NemlaSelectField(
    label: String,
    placeholder: String,
    options: List<NemlaOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelVisible: Boolean = true,
    leading: (@Composable (T) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = NemlaTheme.colors
    val shape = RoundedCornerShape(NemlaTheme.radius.md)
    val selectedLabel = options.firstOrNull { it.value == selected }?.label
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedLabel.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            textStyle = NemlaTheme.typography.input,
            label = if (labelVisible) ({ Text(label) }) else null,
            placeholder = { Text(placeholder, style = NemlaTheme.typography.input) },
            leadingIcon = if (leading != null && selected != null) ({ leading(selected) }) else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = shape,
            colors = nemlaOutlinedFieldColors(),
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = label
                        stateDescription = selectedLabel ?: placeholder
                    },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = shape,
            containerColor = colors.popover,
        ) {
            options.forEach { option ->
                val isSelected = option.value == selected
                DropdownMenuItem(
                    text = { Text(text = option.label, style = NemlaTheme.typography.body) },
                    leadingIcon = leading?.let { draw -> { draw(option.value) } },
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

@Composable
fun NemlaPickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: ImageVector = NemlaIcons.Calendar,
) {
    val shape = RoundedCornerShape(NemlaTheme.radius.md)
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            textStyle = NemlaTheme.typography.input.copy(fontFeatureSettings = "tnum"),
            label = { Text(label) },
            trailingIcon = { Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(NemlaTheme.sizes.iconLarge)) },
            shape = shape,
            colors = nemlaOutlinedFieldColors(),
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .padding(top = PickerLabelOffset)
                    .clip(shape)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    .semantics {
                        contentDescription = label
                        stateDescription = value
                    },
        )
    }
}

private val PickerLabelOffset = 8.dp
