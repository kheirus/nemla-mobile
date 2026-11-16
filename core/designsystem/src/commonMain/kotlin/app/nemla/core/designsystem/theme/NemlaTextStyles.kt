package app.nemla.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Immutable
class NemlaTextStyles(
    val screenTitle: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val subTitle: TextStyle,
    val brandAuth: TextStyle,
    val brandHeader: TextStyle,
    val amount: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val bodyEmphasis: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
    val label: TextStyle,
    val button: TextStyle,
    val buttonSmall: TextStyle,
    val input: TextStyle,
)

private val displayTracking = (-0.02).em
private val brandTracking = (-0.025).em

internal fun nemlaTextStyles(fonts: NemlaFonts): NemlaTextStyles {
    val display = TextStyle(fontFamily = fonts.display, letterSpacing = displayTracking)
    val body = TextStyle(fontFamily = fonts.body)
    return NemlaTextStyles(
        screenTitle = display.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
        sectionTitle = display.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        cardTitle = display.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        subTitle = display.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        brandAuth =
            display.copy(fontSize = 20.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = brandTracking),
        brandHeader =
            display.copy(fontSize = 24.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = brandTracking),
        amount =
            display.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        body = body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
        bodyStrong = body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        bodyEmphasis = body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = fonts.bodySemiBold),
        caption = body.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
        overline = body.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.05.em),
        label = body.copy(fontSize = 14.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
        button = body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        buttonSmall = body.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        input = body.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    )
}

internal fun NemlaTextStyles.toTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = screenTitle.fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = screenTitle.fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = screenTitle.fontFamily),
        headlineLarge = amount.copy(fontFeatureSettings = null),
        headlineMedium = sectionTitle.copy(fontSize = 22.sp),
        headlineSmall = screenTitle,
        titleLarge = sectionTitle,
        titleMedium = cardTitle,
        titleSmall = subTitle,
        bodyLarge = input,
        bodyMedium = body,
        bodySmall = caption,
        labelLarge = button,
        labelMedium = buttonSmall,
        labelSmall = caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
}
