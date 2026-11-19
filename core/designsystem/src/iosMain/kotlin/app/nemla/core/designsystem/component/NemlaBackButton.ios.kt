package app.nemla.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme

@Composable
internal actual fun PlatformBackButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(start = 0.dp, end = NemlaTheme.spacing.xs),
        colors = ButtonDefaults.textButtonColors(contentColor = NemlaTheme.colors.primary),
    ) {
        Icon(NemlaIcons.ChevronLeft, contentDescription = null, modifier = Modifier.size(28.dp))
        Text(label, style = NemlaTheme.typography.input)
    }
}
