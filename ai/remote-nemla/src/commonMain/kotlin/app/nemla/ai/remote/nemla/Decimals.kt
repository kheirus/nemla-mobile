package app.nemla.ai.remote.nemla

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

private val decimalPattern = Regex("^([+-])?([0-9]+)(?:\\.([0-9]*))?(?:[eE]([+-]?[0-9]{1,3}))?$")

private const val MAX_CENT_DIGITS = 17

internal fun decimalToCents(raw: String): Long? {
    val match = decimalPattern.matchEntire(raw.trim()) ?: return null
    val (sign, integer, fraction, exponent) = match.destructured
    val digits = (integer + fraction).trimStart('0').ifEmpty { "0" }
    val shift = 2 - fraction.length + (exponent.toIntOrNull() ?: 0)
    val magnitude =
        if (shift >= 0) {
            if (digits.length + shift > MAX_CENT_DIGITS) return null
            (digits + "0".repeat(shift)).toLong()
        } else {
            val padded = digits.padStart(-shift + 1, '0')
            val kept = padded.dropLast(-shift)
            if (kept.length > MAX_CENT_DIGITS) return null
            val roundUp = padded[padded.length + shift] >= '5'
            kept.toLong() + if (roundUp) 1 else 0
        }
    return if (sign == "-") -magnitude else magnitude
}

internal fun JsonElement?.primitiveContent(): String? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

internal fun JsonElement?.toCents(): Long? = primitiveContent()?.let(::decimalToCents)

internal fun JsonElement?.toDecimal(): Double? = primitiveContent()?.trim()?.toDoubleOrNull()
