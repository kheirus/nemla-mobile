package app.nemla.ai.local

internal class ParityCase(
    val name: String,
    val input: String,
    val expected: String,
    val redactions: Int,
) {
    override fun toString(): String = name
}

internal val serverParityCases: List<ParityCase> =
    listOf(
        ParityCase(
            "statement",
            "BANQUE EXEMPLE\nM. JEAN DUPONT\n12 RUE DES LILAS\n75011 PARIS\nRelevé de compte du 1 mars 2026 au 31 mars 2026\nDate Libellé Débit Crédit\n02/03/2026 CB CARREFOUR MARKET 02/03 45,20\n05/03/2026 VIR SEPA DUPONT JEAN 120,00\n06/03/2026 PRLV LILAS ASSURANCE 30,00",
            "[EN-TÊTE PERSONNEL SUPPRIMÉ]\nRelevé de compte du 1 mars 2026 au 31 mars 2026\nDate Libellé Débit Crédit\n02/03/2026 CB CARREFOUR MARKET 02/03 45,20\n05/03/2026 VIR SEPA [NOM] [NOM] 120,00\n06/03/2026 PRLV [NOM] ASSURANCE 30,00",
            7,
        ),
        ParityCase(
            "headerWithoutTable",
            "LIGNE 1 MARTIN\nLIGNE 2 MARTIN\nLIGNE 3 MARTIN\nLIGNE 4 MARTIN\nLIGNE 5 MARTIN\nLIGNE 6 MARTIN\nLIGNE 7 MARTIN\nLIGNE 8 MARTIN\nLIGNE 9 MARTIN\nLIGNE 10 MARTIN\nLIGNE 11 MARTIN\nLIGNE 12 MARTIN\nLIGNE 13 MARTIN",
            "[EN-TÊTE PERSONNEL SUPPRIMÉ]\n[NOM] 13 [NOM]",
            14,
        ),
        ParityCase(
            "iban",
            "Relevé de compte\nDate Libellé Débit Crédit\nVIR SEPA FR76 3000 6000 0112 3456 7890 189 12,50",
            "Relevé de compte\nDate Libellé Débit Crédit\nVIR SEPA [IBAN] 189 12,50",
            1,
        ),
        ParityCase(
            "bic",
            "Relevé de compte\nDate Libellé Débit Crédit\nVIR RECU BIC: BNPAFRPPXXX 50,00",
            "Relevé de compte\nDate Libellé Débit Crédit\nVIR RECU BIC : [BIC] 50,00",
            1,
        ),
        ParityCase(
            "rib",
            "Relevé de compte\nDate Libellé Débit Crédit\nRIB : 30004 00550 00012345678 45",
            "Relevé de compte\nDate Libellé Débit Crédit\nRIB : [RIB]",
            1,
        ),
        ParityCase(
            "maskedCard",
            "Relevé de compte\nDate Libellé Débit Crédit\nCB 4970XXXXXX1234 LIDL 12,30",
            "Relevé de compte\nDate Libellé Débit Crédit\nCB [CARTE] LIDL 12,30",
            1,
        ),
        ParityCase(
            "cardNumber",
            "Relevé de compte\nDate Libellé Débit Crédit\nPAIEMENT 4970 1012 3456 7890 MONOP 3,20",
            "Relevé de compte\nDate Libellé Débit Crédit\nPAIEMENT [CARTE]MONOP 3,20",
            1,
        ),
        ParityCase(
            "longNumber",
            "Relevé de compte\nDate Libellé Débit Crédit\nPRLV EDF REF 123456789 54,00",
            "Relevé de compte\nDate Libellé Débit Crédit\nPRLV EDF REF [NUMERO] 54,00",
            1,
        ),
        ParityCase(
            "email",
            "Relevé de compte\nDate Libellé Débit Crédit\nVIR jean.dupont@example.fr 10,00",
            "Relevé de compte\nDate Libellé Débit Crédit\nVIR [EMAIL] 10,00",
            1,
        ),
        ParityCase(
            "internationalPhone",
            "Relevé de compte\nDate Libellé Débit Crédit\nAPPEL +33 6 12 34 56 78 2,00",
            "Relevé de compte\nDate Libellé Débit Crédit\nAPPEL [TEL] 2,00",
            1,
        ),
        ParityCase(
            "labelledPhone",
            "Relevé de compte\nDate Libellé Débit Crédit\nTél. agence : 01 23 45 67 89",
            "Relevé de compte\nDate Libellé Débit Crédit\nTél. : [TEL]",
            1,
        ),
        ParityCase(
            "postcodeCity",
            "Relevé de compte\nDate Libellé Débit Crédit\nACHAT 75001 PARIS 12,50",
            "Relevé de compte\nDate Libellé Débit Crédit\nACHAT [VILLE]12,50",
            1,
        ),
        ParityCase(
            "address",
            "Relevé de compte\nDate Libellé Débit Crédit\nPARKING PLACE DE LA GARE 12,50",
            "Relevé de compte\nDate Libellé Débit Crédit\nPARKING [ADRESSE],50",
            1,
        ),
        ParityCase(
            "numberedAddress",
            "Relevé de compte\nDate Libellé Débit Crédit\nLIVRAISON 12 bis rue Victor Hugo, 7,90",
            "Relevé de compte\nDate Libellé Débit Crédit\nLIVRAISON [ADRESSE], 7,90",
            1,
        ),
        ParityCase(
            "taggedName",
            "Relevé de compte\nDate Libellé Débit Crédit\nVIR SEPA /DE MARIE CURIE /MOTIF LOYER 600,00",
            "Relevé de compte\nDate Libellé Débit Crédit\nVIR SEPA /DE [NOM]/MOTIF LOYER 600,00",
            1,
        ),
        ParityCase(
            "civility",
            "Relevé de compte\nDate Libellé Débit Crédit\nVIR M. Jean Dupont 25,00",
            "Relevé de compte\nDate Libellé Débit Crédit\nVIR [NOM]25,00",
            1,
        ),
        ParityCase(
            "upperNameLine",
            "Relevé de compte\nDate Libellé Débit Crédit\nJEAN DUPONT\nCARREFOUR MARKET\nPRLV SEPA\n12/03 LIDL 4,50",
            "Relevé de compte\nDate Libellé Débit Crédit\n[NOM]\n[NOM]\nPRLV SEPA\n12/03 LIDL 4,50",
            2,
        ),
        ParityCase(
            "upperWordsBeforeSlash",
            "Relevé de compte\nDate Libellé Débit Crédit\nPRLV FREE MOBILE/ABONNEMENT 19,99",
            "Relevé de compte\nDate Libellé Débit Crédit\nPRLV [NOM]/ABONNEMENT 19,99",
            1,
        ),
        ParityCase(
            "personalLines",
            "Relevé de compte\nDate Libellé Débit Crédit\nTitulaire du compte : Jean Dupont\nNuméro client 445566\nPrénom Marie\n03/03 SNCF 45,00",
            "Relevé de compte\nDate Libellé Débit Crédit\n[LIGNE PERSONNELLE SUPPRIMÉE]\n[LIGNE PERSONNELLE SUPPRIMÉE]\n[LIGNE PERSONNELLE SUPPRIMÉE]\n03/03 SNCF 45,00",
            3,
        ),
        ParityCase(
            "falsePositives",
            "Relevé de compte\nDate Libellé Débit Crédit\n02/03/2026 BOULANGERIE PAUL 3,80\n15/03/2026 AMAZON MKTPLACE 1 234,56\n20/03/2026 CB NETFLIX.COM 13,49\nSolde au 31/03/2026 2 345,67\nTotal des opérations 1 250,00",
            "Relevé de compte\nDate Libellé Débit Crédit\n02/03/2026 BOULANGERIE PAUL 3,80\n15/03/2026 AMAZON MKTPLACE 1 234,56\n20/03/2026 CB NETFLIX.COM 13,49\nSolde au 31/03/2026 2 345,67\nTotal des opérations 1 250,00",
            0,
        ),
        ParityCase(
            "accentedHolder",
            "MME HÉLÈNE MÜLLER\nDate Libellé Débit Crédit\nVIR SEPA HÉLÈNE 10,00\nREMBOURSEMENT Hélène Müller 5,00",
            "[EN-TÊTE PERSONNEL SUPPRIMÉ]\nDate Libellé Débit Crédit\nVIR SEPA [NOM] 10,00\nREMBOURSEMENT [NOM] [NOM] 5,00",
            4,
        ),
        ParityCase(
            "messyWhitespace",
            "Relevé de compte\r\nDate   Libellé\tDébit\u00A0Crédit\r\n\r\n  02/03  CB   LIDL\u00A0\u00A012,30  \r\n",
            "Relevé de compte\nDate Libellé Débit Crédit\n02/03 CB LIDL 12,30",
            0,
        ),
    )
