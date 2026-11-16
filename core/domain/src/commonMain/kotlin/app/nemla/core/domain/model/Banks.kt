package app.nemla.core.domain.model

data class Bank(
    val id: String,
    val name: String,
    val cutoffDay: Int,
    val debitDay: Int,
)

object Banks {
    val all: List<Bank> =
        listOf(
            Bank("bnp", "BNP Paribas", 31, 5),
            Bank("sg", "Société Générale", 31, 3),
            Bank("credit-agricole", "Crédit Agricole", 25, 1),
            Bank("lcl", "LCL", 31, 5),
            Bank("cic", "CIC", 31, 5),
            Bank("credit-mutuel", "Crédit Mutuel", 31, 5),
            Bank("caisse-epargne", "Caisse d'Épargne", 25, 1),
            Bank("banque-populaire", "Banque Populaire", 25, 1),
            Bank("banque-postale", "La Banque Postale", 31, 1),
            Bank("hsbc", "HSBC", 31, 5),
            Bank(OTHER_ID, "Autre banque", 31, 5),
        )

    const val OTHER_ID: String = "autre"
    const val CUTOFF_MIN: Int = 1
    const val CUTOFF_MAX: Int = 31
    const val DEBIT_MIN: Int = 1
    const val DEBIT_MAX: Int = 28

    fun byId(id: String?): Bank? = all.firstOrNull { it.id == id }
}

data class DebitSettings(
    val bankId: String?,
    val rule: DeferredDebit,
    val choiceMade: Boolean,
)
