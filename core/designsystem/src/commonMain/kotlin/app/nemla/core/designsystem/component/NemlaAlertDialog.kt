package app.nemla.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.DialogProperties
import app.nemla.core.designsystem.theme.NemlaTheme

@Composable
fun NemlaAlertDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    dismissLabel: String? = null,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    dismissible: Boolean = true,
) {
    val colors = NemlaTheme.colors
    AlertDialog(
        onDismissRequest = { if (dismissible) onDismissRequest() },
        modifier = modifier,
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        iconContentColor = colors.primary,
        titleContentColor = colors.foreground,
        textContentColor = colors.mutedForeground,
        icon = icon?.let { vector -> { Icon(vector, contentDescription = null) } },
        title = { Text(title, style = NemlaTheme.typography.cardTitle) },
        text = text?.let { body -> { Text(body, style = NemlaTheme.typography.body) } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    style = NemlaTheme.typography.button,
                    color = if (destructive) colors.destructive else colors.primary,
                )
            }
        },
        dismissButton =
            dismissLabel?.let { label ->
                {
                    TextButton(onClick = onDismissRequest) {
                        Text(label, style = NemlaTheme.typography.button, color = colors.foreground)
                    }
                }
            },
    )
}
