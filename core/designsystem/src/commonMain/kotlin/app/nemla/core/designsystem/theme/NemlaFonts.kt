package app.nemla.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

@Immutable
internal data class NemlaFonts(
    val display: FontFamily,
    val body: FontFamily,
    val bodySemiBold: FontWeight,
)

@Composable
internal fun nemlaFonts(): NemlaFonts =
    NemlaFonts(
        display = FontFamily.Default,
        body = FontFamily.Default,
        bodySemiBold = FontWeight.Bold,
    )
