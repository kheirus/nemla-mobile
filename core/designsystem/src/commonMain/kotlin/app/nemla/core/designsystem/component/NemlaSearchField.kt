package app.nemla.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.resources.Res
import app.nemla.core.designsystem.resources.ds_clear_text
import app.nemla.core.designsystem.theme.NemlaTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun NemlaSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailingActions: @Composable RowScope.() -> Unit = {},
) {
    val colors = NemlaTheme.colors
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = NemlaTheme.typography.input,
        placeholder = { Text(placeholder, style = NemlaTheme.typography.input) },
        leadingIcon = { Icon(NemlaIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(NemlaIcons.X, contentDescription = stringResource(Res.string.ds_clear_text), modifier = Modifier.size(18.dp))
                    }
                }
                trailingActions()
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(percent = 50),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.foreground,
                unfocusedTextColor = colors.foreground,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = colors.primary,
                focusedBorderColor = colors.ring,
                unfocusedBorderColor = colors.input,
                focusedLeadingIconColor = colors.mutedForeground,
                unfocusedLeadingIconColor = colors.mutedForeground,
                focusedTrailingIconColor = colors.foreground,
                unfocusedTrailingIconColor = colors.foreground,
                focusedPlaceholderColor = colors.mutedForeground,
                unfocusedPlaceholderColor = colors.mutedForeground,
            ),
    )
}
