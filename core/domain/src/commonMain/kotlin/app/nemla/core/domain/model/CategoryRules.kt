package app.nemla.core.domain.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object CategoryPalette {
    val colors: List<String> =
        listOf("#E4572E", "#F2B701", "#D01C4F", "#B5179E", "#2E86AB", "#17395E", "#4361EE", "#00A5A5", "#8C5E3C", "#7A7F87")

    const val DEFAULT_COLOR: String = "#7A7F87"

    private const val HUE_STEPS = 360
    private const val HUE_STRIDE = 37
    private const val SATURATION = 62
    private const val LIGHTNESS = 45

    fun pickUnusedColor(used: Collection<String>): String {
        val taken = used.map { it.trim().uppercase() }.toSet()
        colors.firstOrNull { it.uppercase() !in taken }?.let { return it }
        for (step in 0 until HUE_STEPS) {
            val candidate = hslToHex((step * HUE_STRIDE) % HUE_STEPS, SATURATION, LIGHTNESS)
            if (candidate !in taken) return candidate
        }
        return DEFAULT_COLOR
    }

    fun hslToHex(
        hue: Int,
        saturation: Int,
        lightness: Int,
    ): String {
        val sat = saturation / 100.0
        val lum = lightness / 100.0
        val a = sat * min(lum, 1 - lum)

        fun channel(n: Int): String {
            val k = (n + hue / 30.0) % 12
            val value = lum - a * max(-1.0, min(k - 3, min(9 - k, 1.0)))
            return (value * 255)
                .roundToInt()
                .toString(16)
                .padStart(2, '0')
                .uppercase()
        }
        return "#" + channel(0) + channel(8) + channel(4)
    }
}

object CategoryNames {
    private const val MAX_SUGGESTIONS = 3

    private val suggestions: List<Pair<String, List<String>>> =
        listOf(
            "courses" to listOf("Courses", "Alimentation", "Supermarché"),
            "restaurants" to listOf("Restaurants", "Restos & bars", "Sorties food"),
            "transport" to listOf("Transport", "Déplacements", "Mobilité"),
            "logement" to listOf("Logement", "Loyer & charges", "Habitation"),
            "loisirs" to listOf("Loisirs", "Sorties", "Divertissement"),
            "sante" to listOf("Santé", "Santé & bien-être", "Soins"),
            "abonnements" to listOf("Abonnements", "Abonnements & services", "Forfaits"),
            "shopping" to listOf("Shopping", "Vêtements", "Achats perso"),
            "epargne & investissement" to listOf("Épargne & investissement", "Épargne", "Argent mis de côté"),
            "epargne" to listOf("Épargne & investissement", "Épargne", "Argent mis de côté"),
            "autre" to listOf("Autre", "Divers", "Imprévus"),
        )

    private val notBudgetableWords = listOf("impôt", "impot", "assurance", "loyer", "taxe")

    fun suggestions(current: String): List<String> {
        val key = normalize(current)
        val found =
            suggestions.firstOrNull { it.first == key }?.second
                ?: suggestions.firstOrNull { key.contains(it.first) }?.second
                ?: return emptyList()
        return found.filter { normalize(it) != key }.take(MAX_SUGGESTIONS)
    }

    fun isBudgetable(category: Category): Boolean {
        if (category.type == CategoryType.FIXED) return false
        val name = category.name.lowercase()
        return notBudgetableWords.none { name.contains(it) }
    }

    fun normalize(value: String): String = removeDiacritics(value.lowercase()).trim()

    private fun removeDiacritics(value: String): String =
        buildString {
            value.forEach { char -> append(diacritics[char] ?: char) }
        }

    private val diacritics: Map<Char, Char> =
        buildMap {
            "àáâãäå".forEach { put(it, 'a') }
            "çć".forEach { put(it, 'c') }
            "èéêë".forEach { put(it, 'e') }
            "ìíîï".forEach { put(it, 'i') }
            "ñ".forEach { put(it, 'n') }
            "òóôõö".forEach { put(it, 'o') }
            "ùúûü".forEach { put(it, 'u') }
            "ýÿ".forEach { put(it, 'y') }
        }
}
