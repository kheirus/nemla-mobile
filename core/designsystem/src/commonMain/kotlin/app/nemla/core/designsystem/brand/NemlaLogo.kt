package app.nemla.core.designsystem.brand

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.resources.Res
import app.nemla.core.designsystem.resources.ds_brand_name
import app.nemla.core.designsystem.theme.NemlaTheme
import org.jetbrains.compose.resources.stringResource

enum class NemlaLogoSize { Auth, Header }

@Composable
fun NemlaLogo(
    modifier: Modifier = Modifier,
    size: NemlaLogoSize = NemlaLogoSize.Auth,
) {
    val name = stringResource(Res.string.ds_brand_name)
    val markSize = if (size == NemlaLogoSize.Auth) NemlaTheme.sizes.logoAuth else NemlaTheme.sizes.logoHeader
    val textStyle = if (size == NemlaLogoSize.Auth) NemlaTheme.typography.brandAuth else NemlaTheme.typography.brandHeader
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = name },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NemlaLogoMark(markSize)
        Text(
            text = name,
            style = textStyle,
            color = NemlaTheme.colors.primary,
            modifier = Modifier.offset(y = 4.dp),
        )
    }
}

@Composable
fun NemlaLogoMark(
    size: Dp,
    containerColor: Color = NemlaTheme.colors.primary,
    contentColor: Color = NemlaTheme.colors.primaryForeground,
) {
    PlaceholderLogoMark(size, containerColor, contentColor)
}

@Composable
private fun PlaceholderLogoMark(
    size: Dp,
    containerColor: Color,
    contentColor: Color,
) {
    Box(
        modifier = Modifier.size(size).background(containerColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = PlaceholderMonogram,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

private val PlaceholderMonogram: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "NemlaMonogram",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes("M7 19V9M7 13a5 5 0 0 1 10 0v6"),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 3f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
}
