package app.nemla.core.domain

import app.nemla.core.domain.model.MerchantKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MerchantKeysTest {
    @Test
    fun accentsCaseAndPunctuationAreFolded() {
        assertEquals("boulangerie paul", MerchantKeys.normalize("Boulangerie PAUL !"))
        assertEquals("cafe creme", MerchantKeys.normalize("Café-crème"))
        assertEquals("noel", MerchantKeys.normalize("Noël"))
    }

    @Test
    fun stopwordsShortWordsAndNumbersAreDropped() {
        assertEquals("marche", MerchantKeys.normalize("Au marché hier pour 12 euros"))
        assertEquals("carrefour", MerchantKeys.normalize("chez Carrefour le 2024"))
        assertEquals("hui", MerchantKeys.normalize("aujourd'hui"))
    }

    @Test
    fun atMostThreeWordsAreKept() {
        assertEquals("grand magasin centre", MerchantKeys.normalize("Grand magasin centre ville Paris"))
    }

    @Test
    fun charactersWithoutCanonicalDecompositionBecomeSeparators() {
        assertEquals("", MerchantKeys.normalize("bœuf"))
        assertEquals("stra", MerchantKeys.normalize("straße"))
    }

    @Test
    fun usableKeysHaveAtLeastThreeCharacters() {
        assertTrue(MerchantKeys.isUsable("abc"))
        assertFalse(MerchantKeys.isUsable("ab"))
        assertFalse(MerchantKeys.isUsable(MerchantKeys.normalize("le 12")))
    }
}
