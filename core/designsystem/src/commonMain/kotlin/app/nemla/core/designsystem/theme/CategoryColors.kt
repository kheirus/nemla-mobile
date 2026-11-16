package app.nemla.core.designsystem.theme

import androidx.compose.ui.graphics.Color

object CategoryColors {
    val Fallback: Color = Color(0xFF7A7F87)
    val OtherGroup: Color = Color(0xFF94A3B8)

    private const val HEX_LENGTH = 6
    private const val OPAQUE = 0xFF000000L

    fun parse(hex: String?): Color {
        val digits = hex?.trim()?.removePrefix("#") ?: return Fallback
        if (digits.length != HEX_LENGTH || digits.any { it !in '0'..'9' && it.lowercaseChar() !in 'a'..'f' }) return Fallback
        return Color(OPAQUE or digits.toLong(radix = 16))
    }
}
