package app.nemla.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.icon.NemlaIcons

@Composable
internal actual fun PlatformBackButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(NemlaIcons.ArrowLeft, contentDescription = label, modifier = Modifier.size(24.dp))
    }
}
