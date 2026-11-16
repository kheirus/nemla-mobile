package app.nemla.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.resources.Res
import app.nemla.core.designsystem.resources.ds_loading
import app.nemla.core.designsystem.theme.NemlaColors
import app.nemla.core.designsystem.theme.NemlaTheme
import org.jetbrains.compose.resources.stringResource

enum class NemlaButtonVariant { Primary, Secondary, Outline, Ghost, Destructive }

private const val DISABLED_ALPHA = 0.5f

@Composable
fun NemlaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NemlaButtonVariant = NemlaButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val colors = NemlaTheme.colors
    val (container, content) = variant.colorsFrom(colors)
    val loadingDescription = stringResource(Res.string.ds_loading)
    val visuallyDisabled = !enabled && !loading
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier =
            modifier
                .defaultMinSize(minHeight = NemlaTheme.sizes.controlHeight)
                .semantics { if (loading) stateDescription = loadingDescription },
        shape = RoundedCornerShape(NemlaTheme.radius.md),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = content,
                disabledContainerColor = if (visuallyDisabled) container.dimmed() else container,
                disabledContentColor = if (visuallyDisabled) content.copy(alpha = DISABLED_ALPHA) else content,
            ),
        border = if (variant == NemlaButtonVariant.Outline) BorderStroke(1.dp, colors.input) else null,
        elevation = null,
        contentPadding = PaddingValues(horizontal = NemlaTheme.spacing.md, vertical = NemlaTheme.spacing.xs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(NemlaTheme.sizes.icon),
                        color = content,
                        strokeWidth = 2.dp,
                    )
                }

                leadingIcon != null -> {
                    Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(NemlaTheme.sizes.icon))
                }
            }
            Text(text = text, style = NemlaTheme.typography.button, textAlign = TextAlign.Center)
        }
    }
}

enum class NemlaLinkTone { Primary, Muted }

@Composable
fun NemlaTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: NemlaLinkTone = NemlaLinkTone.Primary,
    enabled: Boolean = true,
    underline: Boolean = false,
) {
    val color = if (tone == NemlaLinkTone.Primary) NemlaTheme.colors.primary else NemlaTheme.colors.mutedForeground
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = NemlaTheme.sizes.minTouchTarget),
        shape = RoundedCornerShape(NemlaTheme.radius.md),
        colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = color.copy(alpha = DISABLED_ALPHA)),
        contentPadding = PaddingValues(horizontal = NemlaTheme.spacing.xs, vertical = NemlaTheme.spacing.xs),
    ) {
        Text(
            text = text,
            style =
                NemlaTheme.typography.body.copy(
                    textDecoration = if (underline) TextDecoration.Underline else null,
                ),
            textAlign = TextAlign.Center,
        )
    }
}

private fun NemlaButtonVariant.colorsFrom(colors: NemlaColors): Pair<Color, Color> =
    when (this) {
        NemlaButtonVariant.Primary -> colors.primary to colors.primaryForeground
        NemlaButtonVariant.Secondary -> colors.secondary to colors.secondaryForeground
        NemlaButtonVariant.Outline -> colors.background to colors.foreground
        NemlaButtonVariant.Ghost -> Color.Transparent to colors.foreground
        NemlaButtonVariant.Destructive -> colors.destructive to colors.destructiveForeground
    }

private fun Color.dimmed(): Color = if (this == Color.Transparent) this else copy(alpha = alpha * DISABLED_ALPHA)
