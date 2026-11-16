package app.nemla.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalNemlaColors = staticCompositionLocalOf { LightNemlaColors }
private val LocalNemlaTextStyles = staticCompositionLocalOf<NemlaTextStyles> { error("NemlaTheme is missing") }
private val DefaultSpacing = NemlaSpacing()
private val DefaultRadius = NemlaRadius()
private val DefaultSizes = NemlaSizes()
private val DefaultShapes = DefaultRadius.toShapes()

private val LocalNemlaSpacing = staticCompositionLocalOf { DefaultSpacing }
private val LocalNemlaRadius = staticCompositionLocalOf { DefaultRadius }
private val LocalNemlaSizes = staticCompositionLocalOf { DefaultSizes }

@Composable
fun NemlaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkNemlaColors else LightNemlaColors
    val fonts = nemlaFonts()
    val textStyles = remember(fonts) { nemlaTextStyles(fonts) }
    val colorScheme = remember(colors) { colors.toColorScheme() }
    val typography = remember(textStyles) { textStyles.toTypography() }
    CompositionLocalProvider(
        LocalNemlaColors provides colors,
        LocalNemlaTextStyles provides textStyles,
        LocalNemlaSpacing provides DefaultSpacing,
        LocalNemlaRadius provides DefaultRadius,
        LocalNemlaSizes provides DefaultSizes,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = DefaultShapes) {
            CompositionLocalProvider(LocalContentColor provides colors.foreground, content = content)
        }
    }
}

object NemlaTheme {
    val colors: NemlaColors
        @Composable @ReadOnlyComposable
        get() = LocalNemlaColors.current

    val typography: NemlaTextStyles
        @Composable @ReadOnlyComposable
        get() = LocalNemlaTextStyles.current

    val spacing: NemlaSpacing
        @Composable @ReadOnlyComposable
        get() = LocalNemlaSpacing.current

    val radius: NemlaRadius
        @Composable @ReadOnlyComposable
        get() = LocalNemlaRadius.current

    val sizes: NemlaSizes
        @Composable @ReadOnlyComposable
        get() = LocalNemlaSizes.current
}
