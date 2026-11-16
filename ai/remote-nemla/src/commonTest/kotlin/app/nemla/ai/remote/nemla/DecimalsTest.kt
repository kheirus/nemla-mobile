package app.nemla.ai.remote.nemla

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DecimalsTest {
    @Test
    fun convertsJsonDecimalsToCentsExactly() {
        assertEquals(4_200, decimalToCents("42"))
        assertEquals(4_200, decimalToCents("42.0"))
        assertEquals(120, decimalToCents("1.2"))
        assertEquals(1_349, decimalToCents("13.49"))
        assertEquals(1_010, decimalToCents("10.1"))
        assertEquals(29, decimalToCents("0.29"))
        assertEquals(57, decimalToCents("0.57"))
        assertEquals(1_005, decimalToCents("10.05"))
        assertEquals(-1_250, decimalToCents("-12.5"))
        assertEquals(0, decimalToCents("0"))
        assertEquals(1_200, decimalToCents("12."))
    }

    @Test
    fun roundsExtraDecimalsHalfUp() {
        assertEquals(1_235, decimalToCents("12.345"))
        assertEquals(1_234, decimalToCents("12.3449"))
        assertEquals(1, decimalToCents("0.005"))
        assertEquals(0, decimalToCents("0.004"))
    }

    @Test
    fun handlesExponents() {
        assertEquals(150_000, decimalToCents("1.5E3"))
        assertEquals(12, decimalToCents("1.2e-1"))
        assertEquals(1, decimalToCents("5e-3"))
    }

    @Test
    fun rejectsMalformedOrHugeValues() {
        assertNull(decimalToCents(""))
        assertNull(decimalToCents("abc"))
        assertNull(decimalToCents("1,5"))
        assertNull(decimalToCents("NaN"))
        assertNull(decimalToCents("1e300"))
        assertNull(decimalToCents("123456789012345678901"))
    }

    @Test
    fun readsNumbersAndStringsFromJson() {
        assertEquals(1_349, JsonPrimitive(13.49).toCents())
        assertEquals(1_349, JsonPrimitive("13.49").toCents())
        assertNull(JsonNull.toCents())
        assertNull(JsonPrimitive(true).toCents())
        assertNull(null.toCents())
    }
}
