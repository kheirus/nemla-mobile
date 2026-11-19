package app.nemla.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.nemla.core.designsystem.resources.Res
import app.nemla.core.designsystem.resources.ds_back
import org.jetbrains.compose.resources.stringResource

@Composable
fun NemlaBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    PlatformBackButton(onClick = onClick, label = label ?: stringResource(Res.string.ds_back), modifier = modifier)
}

@Composable
internal expect fun PlatformBackButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier,
)
