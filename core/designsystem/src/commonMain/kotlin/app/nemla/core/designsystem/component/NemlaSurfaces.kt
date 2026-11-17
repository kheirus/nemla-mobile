package app.nemla.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme

@Composable
fun NemlaCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = NemlaTheme.spacing.cardPadding,
    borderColor: Color = NemlaTheme.colors.border,
    containerColor: Color = NemlaTheme.colors.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NemlaTheme.colors
    val shape = RoundedCornerShape(NemlaTheme.radius.xl)
    Surface(
        modifier =
            modifier.shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = colors.shadow.copy(alpha = 0.04f),
                spotColor = colors.shadow.copy(alpha = 0.06f),
            ),
        shape = shape,
        color = containerColor,
        contentColor = colors.cardForeground,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

enum class NemlaBannerKind { Error, Info, Success, Neutral }

@Composable
fun NemlaBanner(
    message: String,
    modifier: Modifier = Modifier,
    kind: NemlaBannerKind = NemlaBannerKind.Info,
    icon: ImageVector? = null,
    announce: Boolean = true,
) {
    val colors = NemlaTheme.colors
    val (tint, container, border) =
        when (kind) {
            NemlaBannerKind.Error -> {
                Triple(
                    colors.destructive,
                    colors.destructive.copy(alpha = 0.05f).compositeOver(colors.card),
                    colors.destructive.copy(alpha = 0.40f),
                )
            }

            NemlaBannerKind.Info -> {
                Triple(
                    colors.accent,
                    colors.accent.copy(alpha = 0.10f).compositeOver(colors.card),
                    colors.accent.copy(alpha = 0.40f),
                )
            }

            NemlaBannerKind.Success -> {
                Triple(
                    colors.primary,
                    colors.primary.copy(alpha = 0.10f).compositeOver(colors.card),
                    colors.primary.copy(alpha = 0.40f),
                )
            }

            NemlaBannerKind.Neutral -> {
                Triple(colors.primary, colors.muted.copy(alpha = 0.60f), Color.Transparent)
            }
        }
    val resolvedIcon =
        icon ?: when (kind) {
            NemlaBannerKind.Error -> NemlaIcons.AlertTriangle
            NemlaBannerKind.Success -> NemlaIcons.Check
            NemlaBannerKind.Info, NemlaBannerKind.Neutral -> NemlaIcons.Info
        }
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { if (announce) liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(NemlaTheme.radius.lg),
        color = container,
        contentColor = colors.foreground,
        border = if (border == Color.Transparent) null else BorderStroke(1.dp, border),
    ) {
        Row(
            modifier = Modifier.padding(NemlaTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = resolvedIcon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.padding(top = 2.dp).size(NemlaTheme.sizes.icon),
            )
            Text(
                text = message,
                style = if (kind == NemlaBannerKind.Neutral) NemlaTheme.typography.caption else NemlaTheme.typography.body,
                color = if (kind == NemlaBannerKind.Neutral) colors.mutedForeground else colors.foreground,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun NemlaEmptyState(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = NemlaTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(PaddingValues(vertical = NemlaTheme.spacing.md)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = colors.accent.copy(alpha = 0.20f).compositeOver(colors.card),
            contentColor = colors.accent,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(NemlaTheme.spacing.sm).size(NemlaTheme.sizes.iconLarge),
            )
        }
        Text(text = title, style = NemlaTheme.typography.cardTitle, color = colors.foreground, textAlign = TextAlign.Center)
        Text(text = message, style = NemlaTheme.typography.body, color = colors.mutedForeground, textAlign = TextAlign.Center)
    }
}

@Composable
fun NemlaCategoryDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clip(CircleShape).background(color))
}
