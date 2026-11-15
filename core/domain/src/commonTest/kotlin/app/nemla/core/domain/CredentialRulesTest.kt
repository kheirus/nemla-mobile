package app.nemla.core.domain

import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.common.ValidationCode
import app.nemla.core.domain.model.EmailAddress
import app.nemla.core.domain.model.Password
import app.nemla.core.domain.validation.CredentialRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialRulesTest {
    @Test
    fun acceptsWellFormedEmails() {
        listOf("a@b.co", "  jane.doe+budget@example.fr ", "x@sub.domain.org").forEach {
            assertNull(CredentialRules.emailError(it), it)
        }
    }

    @Test
    fun rejectsMissingOrMalformedEmails() {
        assertEquals(ValidationCode.EMAIL_REQUIRED, CredentialRules.emailError(""))
        assertEquals(ValidationCode.EMAIL_REQUIRED, CredentialRules.emailError("   "))
        listOf("plain", "a@b", "@b.co", "a@.co", "a b@c.fr", "a@b c.fr").forEach {
            assertEquals(ValidationCode.EMAIL_INVALID, CredentialRules.emailError(it), it)
        }
        assertEquals(ValidationCode.EMAIL_INVALID, CredentialRules.emailError("a".repeat(250) + "@b.fr"))
    }

    @Test
    fun parsedEmailIsTrimmed() {
        val parsed = EmailAddress.parse("  jane@example.fr ")
        assertTrue(parsed is AppResult.Success)
        assertEquals("jane@example.fr", parsed.value.value)
    }

    @Test
    fun passwordNeedsAtLeastSixCharacters() {
        assertEquals(ValidationCode.PASSWORD_REQUIRED, CredentialRules.passwordError(""))
        assertEquals(ValidationCode.PASSWORD_TOO_SHORT, CredentialRules.passwordError("12345"))
        assertNull(CredentialRules.passwordError("123456"))
    }

    @Test
    fun passwordLengthCountsCodePoints() {
        assertEquals(ValidationCode.PASSWORD_TOO_SHORT, CredentialRules.passwordError("😀😀😀"))
        assertNull(CredentialRules.passwordError("😀".repeat(6)))
    }

    @Test
    fun passwordIsLimitedToSeventyTwoBytes() {
        assertNull(CredentialRules.passwordError("a".repeat(72)))
        assertEquals(ValidationCode.PASSWORD_TOO_LONG, CredentialRules.passwordError("a".repeat(73)))
        assertEquals(ValidationCode.PASSWORD_TOO_LONG, CredentialRules.passwordError("é".repeat(37)))
    }

    @Test
    fun blankPasswordIsTooWeak() {
        assertEquals(ValidationCode.PASSWORD_TOO_WEAK, CredentialRules.passwordError("        "))
    }

    @Test
    fun passwordIsKeptVerbatim() {
        val parsed = Password.parse(" secret ")
        assertTrue(parsed is AppResult.Success)
        assertEquals(" secret ", parsed.value.value)
        assertEquals(AppResult.Failure(AppError.Validation(ValidationCode.PASSWORD_TOO_SHORT)), Password.parse("abc"))
    }

    @Test
    fun ageMustStayWithinRange() {
        assertNull(CredentialRules.ageError(null))
        assertNull(CredentialRules.ageError(14))
        assertNull(CredentialRules.ageError(120))
        assertEquals(ValidationCode.AGE_OUT_OF_RANGE, CredentialRules.ageError(13))
        assertEquals(ValidationCode.AGE_OUT_OF_RANGE, CredentialRules.ageError(121))
    }

    @Test
    fun sensitiveValuesAreRedacted() {
        val email = (EmailAddress.parse("jane@example.fr") as AppResult.Success).value
        val password = (Password.parse("hunter22") as AppResult.Success).value
        assertFalse(email.toString().contains("jane"))
        assertFalse(password.toString().contains("hunter"))
        assertFalse(sampleSession.copy(email = email).toString().contains("jane"))
    }
}
