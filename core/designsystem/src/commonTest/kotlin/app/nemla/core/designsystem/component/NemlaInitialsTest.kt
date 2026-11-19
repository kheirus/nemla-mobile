package app.nemla.core.designsystem.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NemlaInitialsTest {
    @Test
    fun singleWordGivesOneLetter() {
        assertEquals("K", nemlaInitials("kheir"))
    }

    @Test
    fun severalWordsGiveTwoLetters() {
        assertEquals("JM", nemlaInitials("jean-marc dupont"))
    }

    @Test
    fun blankNameGivesNothing() {
        assertNull(nemlaInitials("   "))
        assertNull(nemlaInitials(null))
    }
}
