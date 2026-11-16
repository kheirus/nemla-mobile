package app.nemla.core.domain.model

object AmountInput {
    private const val MAX_INTEGER_DIGITS = 10
    private val editable = Regex("^-?[0-9]{0,$MAX_INTEGER_DIGITS}([.,][0-9]{0,2})?$")

    fun isEditable(text: String): Boolean = editable.matches(text.trim())

    fun parseCents(text: String): Long? {
        val trimmed = text.trim()
        if (!editable.matches(trimmed)) return null
        val negative = trimmed.startsWith('-')
        val unsigned = trimmed.removePrefix("-").replace(',', '.')
        val integerPart = unsigned.substringBefore('.')
        val fractionPart = unsigned.substringAfter('.', missingDelimiterValue = "")
        val units = integerPart.ifEmpty { "0" }.toLong()
        val cents = fractionPart.padEnd(2, '0').toLong()
        val magnitude = units * 100 + cents
        return if (negative) -magnitude else magnitude
    }

    fun format(
        cents: Long,
        decimalSeparator: Char = '.',
    ): String {
        val negative = cents < 0
        val magnitude = if (negative) -cents else cents
        val fraction = (magnitude % 100).toString().padStart(2, '0')
        return (if (negative) "-" else "") + (magnitude / 100) + decimalSeparator + fraction
    }
}
