package app.nemla.core.domain.validation

import app.nemla.core.common.ValidationCode

object CredentialRules {
    const val EMAIL_MAX_LENGTH: Int = 254
    const val PASSWORD_MIN_LENGTH: Int = 6
    const val PASSWORD_MAX_BYTES: Int = 72
    const val AGE_MIN: Int = 14
    const val AGE_MAX: Int = 120

    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun emailError(raw: String): ValidationCode? {
        val email = raw.trim()
        return when {
            email.isEmpty() -> ValidationCode.EMAIL_REQUIRED
            email.length > EMAIL_MAX_LENGTH -> ValidationCode.EMAIL_INVALID
            !emailPattern.matches(email) -> ValidationCode.EMAIL_INVALID
            else -> null
        }
    }

    fun passwordError(raw: String): ValidationCode? =
        when {
            raw.isEmpty() -> ValidationCode.PASSWORD_REQUIRED
            raw.codePointLength() < PASSWORD_MIN_LENGTH -> ValidationCode.PASSWORD_TOO_SHORT
            raw.encodeToByteArray().size > PASSWORD_MAX_BYTES -> ValidationCode.PASSWORD_TOO_LONG
            raw.isBlank() -> ValidationCode.PASSWORD_TOO_WEAK
            else -> null
        }

    fun ageError(age: Int?): ValidationCode? = if (age == null || age in AGE_MIN..AGE_MAX) null else ValidationCode.AGE_OUT_OF_RANGE

    private fun String.codePointLength(): Int {
        var count = 0
        var index = 0
        while (index < length) {
            val char = this[index]
            index += if (char.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) 2 else 1
            count++
        }
        return count
    }
}
