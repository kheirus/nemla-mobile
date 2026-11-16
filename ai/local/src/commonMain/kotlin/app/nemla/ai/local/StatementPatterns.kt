package app.nemla.ai.local

internal object StatementPatterns {
    private const val WORD = "[A-Za-z0-9_]"
    const val BOUNDARY = "(?:(?<=$WORD)(?!$WORD)|(?<!$WORD)(?=$WORD))"
    const val SPACE = "[\t\n\u000B\u000C\r    -     　]"
    private const val DIGIT = "[0-9]"
    private const val ANY = "[^\n\r  ]"
    private const val UPPER = "A-ZÀ-Ÿ"
    private const val LATIN = "A-Za-zÀ-ÿ"

    private val insensitive = setOf(RegexOption.IGNORE_CASE)

    private fun regex(
        pattern: String,
        ignoreCase: Boolean = false,
    ): Regex = if (ignoreCase) Regex(pattern, insensitive) else Regex(pattern)

    val tableHeader =
        regex(
            "(date$SPACE+(nature|libell)|nature des op|d[ée]bit$SPACE+cr[ée]dit|solde$SPACE+(cr[ée]diteur|d[ée]biteur))",
            ignoreCase = true,
        )
    val keptHeaderLine = regex("(relev[ée] de compte|du $DIGIT{1,2} $ANY{3,10} $DIGIT{4} au )", ignoreCase = true)
    val trailingUpperWords = regex("[$UPPER]{3,}$SPACE+[$UPPER]{3,}$SPACE*$")
    val upperWord = regex("[$UPPER][$UPPER'’-]{2,}")

    val iban = regex("$BOUNDARY[A-Z]{2}$DIGIT{2}(?:[ ]?[A-Z0-9]{4}){2,7}$BOUNDARY")
    val compactIban = regex("$BOUNDARY[A-Z]{2}$DIGIT{2}[A-Z0-9]{11,30}$BOUNDARY")
    val bic = regex("${BOUNDARY}BIC$SPACE*:?$SPACE*[A-Z0-9]{8,11}$BOUNDARY", ignoreCase = true)
    val rib = regex("${BOUNDARY}RIB$SPACE*:?$SPACE*[0-9 ]{5,30}", ignoreCase = true)
    val maskedCard = regex("$BOUNDARY$DIGIT{2,8}[X*]{3,12}$DIGIT{2,6}$BOUNDARY", ignoreCase = true)
    val cardNumber = regex("$BOUNDARY(?:$DIGIT[ -]?){13,19}$BOUNDARY")
    val longNumber = regex("$BOUNDARY$DIGIT{8,}$BOUNDARY")
    val email = regex("[A-Za-z0-9_.+-]+@[A-Za-z0-9_-]+\\.[A-Za-z0-9_.]+")
    val internationalPhone = regex("\\+$DIGIT{1,3}[ .-]?$DIGIT(?:[ .-]?$DIGIT{2}){4}$BOUNDARY")
    val labelledPhone = regex("${BOUNDARY}T[ée]l\\.?$SPACE*(?:agence)?$SPACE*:?$SPACE*[0-9 .+-]{8,}", ignoreCase = true)
    val nationalPhone =
        regex("${BOUNDARY}0[1-9](?:(?: $DIGIT{2}){4}|(?:\\.$DIGIT{2}){4}|(?:-$DIGIT{2}){4})$BOUNDARY")
    val postcodeCity = regex("$BOUNDARY$DIGIT{5}$SPACE+[$UPPER][$LATIN'’ -]{2,}$BOUNDARY")

    private const val ADDRESS_KEYWORDS =
        "rue|avenue|av\\.|boulevard|bd|impasse|chemin|all[ée]e|place|route|quai|cours|square|villa|passage|" +
            "lieu[- ]dit|hameau|r[ée]sidence|b[âa]timent|bat\\.?|escalier|[ée]tage|porte|appartement|appt|" +
            "bo[îi]te postale|bp|cedex|street|road|avenue"

    private const val AMOUNT = "(?<!$DIGIT)[-+]?$DIGIT{1,3}(?:[ .\u00A0\u202F]$DIGIT{3})*[,.]$DIGIT{2}(?!$DIGIT)"

    val address =
        regex(
            "$BOUNDARY(?:$DIGIT{1,4}$SPACE?(?:bis|ter)?$SPACE?)?(?:$ADDRESS_KEYWORDS)$BOUNDARY(?:(?!$SPACE*$AMOUNT)[^\n,;])*",
            ignoreCase = true,
        )

    val taggedName = regex("(/(?:DE|BEN|BENEF|BENEFICIAIRE|REFBEN)$SPACE+)[$UPPER][$UPPER'’ -]{2,40}")
    val civilityName = regex("$BOUNDARY(?:M\\.|MR|MME|MLLE|MONSIEUR|MADAME)$SPACE+[$UPPER][$LATIN'’ -]{2,40}")
    val upperNameLine = Regex("^[$UPPER]{3,}(?:$SPACE+[$UPPER]{2,}){1,2}$", RegexOption.MULTILINE)
    val upperWordsBeforeSlash = regex("$BOUNDARY[$UPPER]{3,}$SPACE+[$UPPER]{3,}$BOUNDARY(?=$SPACE*/)")
    val whitespaceRun = regex("$SPACE+")

    val personalLine =
        regex("(titulaire|account holder|nom$SPACE*:|pr[ée]nom|client$SPACE*n|num[ée]ro client)", ignoreCase = true)

    val placeholder =
        regex(
            "\\[(?:EN-TÊTE PERSONNEL SUPPRIMÉ|LIGNE PERSONNELLE SUPPRIMÉE|NOM|IBAN|BIC|RIB|CARTE|NUMERO|EMAIL|TEL|VILLE|ADRESSE)\\]",
        )

    val bankVocabulary: Set<String> =
        (
            "RELEVE RELEVÉ COMPTE COMPTES CHEQUE CHEQUES CHÈQUE CHÈQUES BANQUE BANK IBAN BIC RIB SWIFT AGENCE SERVICE " +
                "SERVICES CLIENT CLIENTS TEL TÉL EURO EUROS MONNAIE DATE DATES NATURE OPERATION OPERATIONS OPÉRATIONS " +
                "DEBIT DÉBIT CREDIT CRÉDIT SOLDE CREDITEUR CRÉDITEUR DEBITEUR TOTAL VALEUR PAGE SIEGE SIÈGE SOCIAL " +
                "CAPITAL ORIAS PRLV SEPA VIR VIREMENT PRELEVEMENT FACTURE FACTURES CARTE RETRAIT DAB ECH MDT REF LIB " +
                "MOTIF BEN BENEFICIAIRE DE DU AU ET SA SAS SARL SCPT PORTE BAT BATIMENT ETAGE APPT APPARTEMENT " +
                "RESIDENCE RÉSIDENCE BNP PARIBAS SOCIETE GENERALE CAISSE EPARGNE CREDIT MUTUEL AGRICOLE POSTALE LCL " +
                "BOURSORAMA FORTUNEO REVOLUT N26 HELLO MABANQUE"
        ).split(' ').toSet()

    fun wordToken(token: String): Regex = Regex("$BOUNDARY${escape(token)}$BOUNDARY", insensitive)

    private fun escape(literal: String): String =
        buildString {
            for (char in literal) {
                if (char in REGEX_META) append('\\')
                append(char)
            }
        }

    private const val REGEX_META = "\\^$.|?*+()[]{}"
}
