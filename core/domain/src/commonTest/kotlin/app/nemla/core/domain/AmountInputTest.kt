package app.nemla.core.domain

import app.nemla.core.domain.model.AmountInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AmountInputTest {
    @Test
    fun decimalTextBecomesExactCents() {
        assertEquals(1_250, AmountInput.parseCents("12,5"))
        assertEquals(1_250, AmountInput.parseCents("12.50"))
        assertEquals(1_200, AmountInput.parseCents("12"))
        assertEquals(1_200, AmountInput.parseCents("12."))
        assertEquals(5, AmountInput.parseCents(",05"))
        assertEquals(999_999_999_999, AmountInput.parseCents("9999999999.99"))
    }

    @Test
    fun emptyIsZeroAndNegativeIsKeptLikeTheWeb() {
        assertEquals(0, AmountInput.parseCents(""))
        assertEquals(0, AmountInput.parseCents("  "))
        assertEquals(-300, AmountInput.parseCents("-3"))
    }

    @Test
    fun malformedTextIsRejected() {
        assertNull(AmountInput.parseCents("12,345"))
        assertNull(AmountInput.parseCents("1e3"))
        assertNull(AmountInput.parseCents("douze"))
        assertNull(AmountInput.parseCents("1.2.3"))
        assertFalse(AmountInput.isEditable("12,345"))
        assertTrue(AmountInput.isEditable("12,3"))
        assertTrue(AmountInput.isEditable("-"))
    }

    @Test
    fun centsAreFormattedWithTheRequestedSeparator() {
        assertEquals("12,50", AmountInput.format(1_250, ','))
        assertEquals("0.05", AmountInput.format(5))
        assertEquals("-3.00", AmountInput.format(-300))
    }
}
