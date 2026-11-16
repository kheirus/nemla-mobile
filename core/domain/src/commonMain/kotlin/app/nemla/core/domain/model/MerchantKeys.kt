package app.nemla.core.domain.model

object MerchantKeys {
    const val MIN_USABLE_LENGTH: Int = 3
    private const val MIN_WORD_LENGTH = 3
    private const val MAX_WORDS = 3

    private val stopwords =
        (
            "chez au aux le la les de du des un une et pour avec dans mon ma mes sur par euro euros eur balle balles boules " +
                "environ hier aujourd aujourdhui demain matin soir midi lundi mardi mercredi jeudi vendredi samedi dimanche " +
                "janvier fevrier mars avril mai juin juillet aout septembre octobre novembre decembre depense depenses paye " +
                "payer achat achete achetee achetes dernier derniere ce cette j ai"
        ).split(' ').toSet()

    private val digitsOnly = Regex("^[0-9]+$")

    fun normalize(label: String): String =
        fold(label.lowercase())
            .split(' ')
            .filter { it.length >= MIN_WORD_LENGTH && !digitsOnly.matches(it) && it !in stopwords }
            .take(MAX_WORDS)
            .joinToString(" ")

    fun isUsable(key: String): Boolean = key.length >= MIN_USABLE_LENGTH

    private fun fold(text: String): String {
        val out = StringBuilder(text.length)
        for (char in text) {
            when {
                char in combiningMarks -> Unit
                char in 'a'..'z' || char in '0'..'9' -> out.append(char)
                else -> out.append(decomposedBase(char) ?: ' ')
            }
        }
        return out.toString()
    }

    private fun decomposedBase(char: Char): Char? {
        val index = DECOMPOSABLE.indexOf(char)
        return if (index < 0) null else BASES[index]
    }

    private val combiningMarks = '̀'..'ͯ'

    private const val DECOMPOSABLE =
        "àáâãäåāăąǎ" + "çćĉċč" + "ďḍ" + "èéêëēĕėęěẽ" + "ĝğġģǧ" + "ĥ" + "ìíîïĩīĭįǐ" + "ĵ" + "ķǩ" + "ĺļľ" +
            "ñńņňǹ" + "òóôõöōŏőǒ" + "ŕŗř" + "śŝşšș" + "ţťț" + "ùúûüũūŭůűųǔ" + "ŵ" + "ýÿŷỳ" + "źżž"

    private const val BASES =
        "aaaaaaaaaa" + "ccccc" + "dd" + "eeeeeeeeee" + "ggggg" + "h" + "iiiiiiiii" + "j" + "kk" + "lll" +
            "nnnnn" + "ooooooooo" + "rrr" + "sssss" + "ttt" + "uuuuuuuuuuu" + "w" + "yyyy" + "zzz"
}
