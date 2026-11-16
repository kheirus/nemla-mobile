package app.nemla.core.domain.format

import app.nemla.core.domain.model.Currency

object MoneyFormat {
    private const val NARROW_NBSP = ' '
    private const val NBSP = ' '

    private class Style(
        val symbol: String,
        val prefix: Boolean,
        val group: Char,
        val decimal: Char,
        val minFractionDigits: Int = 2,
    )

    private fun styleOf(currency: Currency): Style =
        when (currency) {
            Currency.EUR -> Style("€", prefix = false, group = NARROW_NBSP, decimal = ',')
            Currency.CHF -> Style("CHF", prefix = false, group = NARROW_NBSP, decimal = ',')
            Currency.GBP -> Style("£", prefix = true, group = ',', decimal = '.')
            Currency.USD -> Style("$", prefix = true, group = ',', decimal = '.')
            Currency.CAD -> Style("$", prefix = false, group = NBSP, decimal = ',')
            Currency.MAD -> Style("MAD", prefix = false, group = '.', decimal = ',')
            Currency.TND -> Style("DT", prefix = false, group = NARROW_NBSP, decimal = ',')
            Currency.DZD -> Style("DA", prefix = false, group = NARROW_NBSP, decimal = ',')
            Currency.XOF -> Style("F CFA", prefix = false, group = NARROW_NBSP, decimal = ',', minFractionDigits = 0)
            Currency.AED -> Style("AED", prefix = false, group = NARROW_NBSP, decimal = ',')
        }

    fun format(
        cents: Long,
        currency: Currency,
    ): String {
        val style = styleOf(currency)
        val negative = cents < 0
        val magnitude = if (negative) -cents else cents
        val units = grouped(magnitude / 100, style.group)
        var fraction = (magnitude % 100).toString().padStart(2, '0')
        while (fraction.length > style.minFractionDigits && fraction.endsWith('0')) fraction = fraction.dropLast(1)
        val number = if (fraction.isEmpty()) units else units + style.decimal + fraction
        val sign = if (negative) "-" else ""
        return if (style.prefix) sign + style.symbol + number else sign + number + NBSP + style.symbol
    }

    fun compact(
        cents: Long,
        currency: Currency,
    ): String {
        val symbol = currency.symbol
        if (cents == 0L) return "0 $symbol"
        val magnitude = if (cents < 0) -cents else cents
        if (magnitude >= THOUSAND_CENTS) {
            val text =
                if (cents >= TEN_THOUSAND_CENTS) {
                    (cents + THOUSAND_CENTS / 2).floorDiv(THOUSAND_CENTS).toString()
                } else {
                    val tenths = roundHalfAwayFromZero(cents, TENTH_OF_THOUSAND_CENTS)
                    val tenthsMagnitude = if (tenths < 0) -tenths else tenths
                    val sign = if (tenths < 0) "-" else ""
                    val whole = tenthsMagnitude / 10
                    val decimal = tenthsMagnitude % 10
                    if (decimal == 0L) "$sign$whole" else "$sign$whole,$decimal"
                }
            return "$text k$symbol"
        }
        return "${(cents + 50).floorDiv(100L)} $symbol"
    }

    private fun roundHalfAwayFromZero(
        value: Long,
        divisor: Long,
    ): Long {
        val magnitude = if (value < 0) -value else value
        val rounded = (magnitude + divisor / 2) / divisor
        return if (value < 0) -rounded else rounded
    }

    private fun grouped(
        units: Long,
        separator: Char,
    ): String {
        val digits = units.toString()
        val builder = StringBuilder()
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) builder.append(separator)
            builder.append(char)
        }
        return builder.toString()
    }

    private const val THOUSAND_CENTS = 100_000L
    private const val TEN_THOUSAND_CENTS = 1_000_000L
    private const val TENTH_OF_THOUSAND_CENTS = 10_000L
}
