package app.nemla.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
class NemlaSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 40.dp,
    val pageHorizontal: Dp = 20.dp,
    val pageVertical: Dp = 24.dp,
    val sectionGap: Dp = 20.dp,
    val fieldGap: Dp = 16.dp,
    val labelGap: Dp = 8.dp,
    val cardPadding: Dp = 20.dp,
)

@Immutable
class NemlaRadius(
    val sm: Dp = 12.dp,
    val md: Dp = 14.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
)

@Immutable
class NemlaSizes(
    val minTouchTarget: Dp = 48.dp,
    val controlHeight: Dp = 48.dp,
    val icon: Dp = 16.dp,
    val iconLarge: Dp = 20.dp,
    val authCardMaxWidth: Dp = 384.dp,
    val contentMaxWidth: Dp = 768.dp,
    val logoAuth: Dp = 40.dp,
    val logoHeader: Dp = 36.dp,
)

internal fun NemlaRadius.toShapes(): Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(sm),
        small = RoundedCornerShape(md),
        medium = RoundedCornerShape(lg),
        large = RoundedCornerShape(xl),
        extraLarge = RoundedCornerShape(xl),
    )
