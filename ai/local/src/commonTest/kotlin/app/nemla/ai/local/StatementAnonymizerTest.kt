package app.nemla.ai.local

import app.nemla.ai.api.AiCapability
import app.nemla.ai.api.AiEngine
import app.nemla.ai.api.ExecutionLocation
import app.nemla.ai.api.PiiKind
import app.nemla.ai.api.RawStatement
import app.nemla.ai.api.RedactionReport
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatementAnonymizerTest {
    private val deliberateDeviations = setOf("address")

    private val prefix = "Relevé de compte\nDate Libellé Débit Crédit\n"

    private fun anonymize(
        text: String,
        names: List<String> = emptyList(),
    ): Anonymization = StatementAnonymizer.anonymize(text, names)

    private fun body(text: String): String = anonymize(prefix + text).text.removePrefix(prefix)

    private fun counts(text: String): Map<PiiKind, Int> = anonymize(prefix + text).report.counts

    @Test
    fun matchesServerOutputForEveryRule() {
        for (case in serverParityCases.filterNot { it.name in deliberateDeviations }) {
            val result = anonymize(case.input)
            assertEquals(case.expected, result.text, case.name)
            assertEquals(case.redactions, result.report.total, case.name)
        }
    }

    @Test
    fun countsEachRuleUnderItsKind() {
        assertEquals(mapOf(PiiKind.IBAN to 1), counts("VIR SEPA FR76 3000 6000 0112 3456 7890 189 12,50"))
        assertEquals(mapOf(PiiKind.BIC to 1), counts("VIR RECU BIC: BNPAFRPPXXX 50,00"))
        assertEquals(mapOf(PiiKind.ACCOUNT_NUMBER to 1), counts("RIB : 30004 00550 00012345678 45"))
        assertEquals(mapOf(PiiKind.CARD_NUMBER to 1), counts("CB 4970XXXXXX1234 LIDL 12,30"))
        assertEquals(mapOf(PiiKind.CARD_NUMBER to 1), counts("PAIEMENT 4970 1012 3456 7890 MONOP 3,20"))
        assertEquals(mapOf(PiiKind.ACCOUNT_NUMBER to 1), counts("PRLV EDF REF 123456789 54,00"))
        assertEquals(mapOf(PiiKind.EMAIL to 1), counts("VIR jean.dupont@example.fr 10,00"))
        assertEquals(mapOf(PiiKind.PHONE to 1), counts("APPEL +33 6 12 34 56 78 2,00"))
        assertEquals(mapOf(PiiKind.PHONE to 1), counts("Tél. agence : 01 23 45 67 89"))
        assertEquals(mapOf(PiiKind.POSTAL_ADDRESS to 1), counts("ACHAT 75001 PARIS 12,50"))
        assertEquals(mapOf(PiiKind.POSTAL_ADDRESS to 1), counts("LIVRAISON 12 bis rue Victor Hugo, 7,90"))
        assertEquals(mapOf(PiiKind.PERSON_NAME to 1), counts("VIR SEPA /DE MARIE CURIE /MOTIF LOYER 600,00"))
        assertEquals(mapOf(PiiKind.PERSON_NAME to 1), counts("VIR M. Jean Dupont 25,00"))
        assertEquals(mapOf(PiiKind.PERSON_NAME to 1), counts("PRLV FREE MOBILE/ABONNEMENT 19,99"))
        assertEquals(mapOf(PiiKind.OTHER to 1), counts("Prénom Marie"))
    }

    @Test
    fun headerTokensAreRemovedEverywhere() {
        val statement = serverParityCases.first { it.name == "statement" }
        val result = anonymize(statement.input)
        assertFalse(result.text.contains("DUPONT"))
        assertFalse(result.text.contains("JEAN"))
        assertFalse(result.text.contains("LILAS"))
        assertEquals(4, result.report.counts[PiiKind.OTHER])
        assertEquals(3, result.report.counts[PiiKind.PERSON_NAME])
    }

    @Test
    fun keepsAmountsDatesAndMerchantLabels() {
        val text =
            "02/03/2026 CB BOULANGERIE PAUL 3,80\n" +
                "15/03/2026 PRLV AMAZON MKTPLACE 1 234,56\n" +
                "20/03/2026 CB NETFLIX.COM 13,49\n" +
                "21/03 RETRAIT DAB 40,00\n" +
                "22/03 VIR SEPA LOYER MARS 650,00\n" +
                "Solde au 31/03/2026 2 345,67"
        assertEquals(text, body(text))
        assertEquals(RedactionReport.Empty, anonymize(prefix + text).report)
    }

    @Test
    fun knownMerchantQuirksFromTheServerArePreserved() {
        assertEquals("[NOM]", body("CARREFOUR MARKET"))
        assertEquals("PRLV SEPA", body("PRLV SEPA"))
    }

    @Test
    fun addressRuleStopsBeforeTheAmountUnlikeTheServer() {
        assertEquals("PARKING [ADRESSE] 12,50", body("PARKING PLACE DE LA GARE 12,50"))
        assertEquals("PEAGE [ADRESSE] 1 234,56", body("PEAGE ROUTE DES ALPES 1 234,56"))
        assertEquals("CB [ADRESSE] -8.40 EUR", body("CB COURS MIRABEAU -8.40 EUR"))
        assertEquals("LIVRAISON [ADRESSE], 7,90", body("LIVRAISON 12 bis rue Victor Hugo, 7,90"))
        assertEquals("CB [ADRESSE]", body("CB 3 PLACE DU MARCHE 1945"))
        assertEquals("VIR [ADRESSE] 15,00", body("VIR RUE DU 8 MAI 15,00"))
    }

    @Test
    fun frenchPhoneNumbersAreRedactedBeyondServerRules() {
        assertEquals("APPEL [TEL] 2,00", body("APPEL 06 12 34 56 78 2,00"))
        assertEquals("APPEL [TEL] 2,00", body("APPEL 01.23.45.67.89 2,00"))
        assertEquals(mapOf(PiiKind.PHONE to 1), counts("APPEL 06-12-34-56-78 2,00"))
        assertEquals("02.03.2026 CB LIDL 12,30", body("02.03.2026 CB LIDL 12,30"))
        assertEquals("03.12.24 45.67", body("03.12.24 45.67"))
    }

    @Test
    fun compactIbanIsRedactedBeyondServerRules() {
        val result = anonymize(prefix + "VIR SEPA FR7630006000011234567890189 12,50")
        assertEquals("VIR SEPA [IBAN] 12,50", result.text.removePrefix(prefix))
        assertEquals(mapOf(PiiKind.IBAN to 1), result.report.counts)
    }

    @Test
    fun accentedHolderNamesMatchCaseInsensitively() {
        val result = anonymize("MME HÉLÈNE MÜLLER\nDate Libellé Débit Crédit\nREMBOURSEMENT Hélène Müller 5,00")
        assertEquals("[EN-TÊTE PERSONNEL SUPPRIMÉ]\nDate Libellé Débit Crédit\nREMBOURSEMENT [NOM] [NOM] 5,00", result.text)
    }

    @Test
    fun knownNamesAreRemovedEverywhere() {
        val result = anonymize(prefix + "VIR SEPA Camille Martin-Durand 20,00\nREMB CAMILLE 5,00", listOf("Camille Martin-Durand"))
        assertEquals("VIR SEPA [NOM] [NOM] 20,00\nREMB [NOM] 5,00", result.text.removePrefix(prefix))
        assertEquals(mapOf(PiiKind.PERSON_NAME to 3), result.report.counts)
    }

    @Test
    fun shortKnownNamesAreIgnored() {
        assertEquals("CB LE BON 3,00", anonymize(prefix + "CB LE BON 3,00", listOf("Le", "Bo")).text.removePrefix(prefix))
    }

    @Test
    fun tokensNeverAlterPlaceholders() {
        val result = anonymize(prefix + "VIR [NOM] NOM 2,00\nCB CARREFOUR MARKET 1,00", listOf("Nom"))
        assertEquals("VIR [NOM] [NOM] 2,00\nCB CARREFOUR MARKET 1,00", result.text.removePrefix(prefix))
    }

    @Test
    fun emptyAndBlankTextsStayEmpty() {
        assertEquals("", anonymize("").text)
        assertEquals(RedactionReport.Empty, anonymize("").report)
        assertEquals("", anonymize(" \n\t\r\n ").text)
    }

    @Test
    fun anonymizingTwiceChangesNothing() {
        val inputs = serverParityCases.map { it.input } + "LIGNE UNIQUE"
        for (input in inputs) {
            val once = anonymize(input, listOf("Jean Dupont"))
            val twice = anonymize(once.text, listOf("Jean Dupont"))
            assertEquals(once.text, twice.text, input)
            assertEquals(0, twice.report.total, input)
        }
    }

    @Test
    fun outputIsTruncatedToServerLimit() {
        val line = "02/03 CB LIDL 12,30\n"
        val long = prefix + line.repeat(StatementAnonymizer.MAX_CHARS / line.length + 10)
        assertEquals(StatementAnonymizer.MAX_CHARS, anonymize(long).text.length)
    }

    @Test
    fun reportsAndTextHideContentInToString() {
        val result = anonymize(prefix + "VIR jean.dupont@example.fr 10,00")
        assertFalse(result.toString().contains("dupont"))
        assertFalse(result.report.toString().contains("dupont"))
    }

    @Test
    fun engineReturnsAnonymizedTextOnDevice() =
        runTest {
            val engine = RuleBasedStatementAnonymizer()
            assertEquals(AiCapability.StatementAnonymization, engine.capability)
            assertEquals(ExecutionLocation.ON_DEVICE, engine.descriptor.location)
            assertEquals(0, engine.descriptor.creditCost)
            val anonymized = engine.execute(RawStatement(prefix + "VIR M. Jean Dupont 25,00", listOf("Dupont")))
            assertEquals(prefix + "VIR [NOM]25,00", anonymized.value)
            assertEquals(1, anonymized.report.total)
            assertFalse(anonymized.toString().contains("Dupont"))
            assertTrue(anonymized.toString().contains("redactions=1"))
        }

    @Test
    fun koinModuleExposesTheEngineAsAiEngine() {
        val koin = koinApplication { modules(aiLocalModule) }.koin
        val engines = koin.getAll<AiEngine<*, *>>()
        assertEquals(listOf(RuleBasedStatementAnonymizer.ENGINE_ID), engines.map { it.descriptor.id })
    }
}
