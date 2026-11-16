package app.nemla.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.resources.Res
import app.nemla.core.designsystem.resources.ds_close
import app.nemla.core.designsystem.theme.NemlaTheme
import org.jetbrains.compose.resources.stringResource

private const val CLOSE_ALPHA = 0.7f

@Composable
fun NemlaDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    dismissible: Boolean = true,
    maxWidth: Dp = NemlaTheme.sizes.authCardMaxWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NemlaTheme.colors
    Dialog(
        onDismissRequest = { if (dismissible) onDismissRequest() },
        properties =
            DialogProperties(
                dismissOnBackPress = dismissible,
                dismissOnClickOutside = dismissible,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Surface(
            modifier = modifier.padding(NemlaTheme.spacing.md).widthIn(max = maxWidth).fillMaxWidth(),
            shape = RoundedCornerShape(NemlaTheme.radius.lg),
            color = colors.background,
            contentColor = colors.foreground,
            border = BorderStroke(1.dp, colors.border),
        ) {
            Box {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(NemlaTheme.spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = NemlaTheme.spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
                    ) {
                        Text(
                            text = title,
                            style = NemlaTheme.typography.cardTitle.copy(fontSize = 18.sp, lineHeight = 22.sp),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (description != null) {
                            Text(
                                text = description,
                                style = NemlaTheme.typography.body,
                                color = colors.mutedForeground,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    content()
                }
                if (dismissible) {
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.align(Alignment.TopEnd).padding(NemlaTheme.spacing.xxs),
                    ) {
                        Icon(
                            imageVector = NemlaIcons.X,
                            contentDescription = stringResource(Res.string.ds_close),
                            modifier = Modifier.size(NemlaTheme.sizes.icon).alpha(CLOSE_ALPHA),
                        )
                    }
                }
            }
        }
    }
}
