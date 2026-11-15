package app.nemla.core.domain.model

enum class Currency(
    val symbol: String,
) {
    EUR("€"),
    CHF("CHF"),
    GBP("£"),
    USD("$"),
    CAD("$ CA"),
    MAD("DH"),
    TND("DT"),
    DZD("DA"),
    XOF("FCFA"),
    AED("AED"),
    ;

    val code: String get() = name

    companion object {
        val Default: Currency = EUR

        fun fromCode(code: String?): Currency = entries.firstOrNull { it.name == code?.trim()?.uppercase() } ?: Default
    }
}

enum class AppLanguage(
    val tag: String,
) {
    FR("fr"),
    EN("en"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = if (tag == EN.tag) EN else FR
    }
}
