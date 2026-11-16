package app.nemla.ai.local

import app.nemla.ai.api.PiiKind
import app.nemla.ai.api.RedactionReport

internal class Anonymization(
    val text: String,
    val report: RedactionReport,
) {
    override fun toString(): String = "Anonymization(length=${text.length}, redactions=${report.total})"
}

internal object StatementAnonymizer {
    const val MAX_CHARS: Int = 90_000
    const val HEADER_PLACEHOLDER: String = "[EN-TÊTE PERSONNEL SUPPRIMÉ]"
    const val LINE_PLACEHOLDER: String = "[LIGNE PERSONNELLE SUPPRIMÉE]"
    private const val HEADER_SCAN_LINES = 12
    private const val MIN_TOKEN_LENGTH = 3
    private val inlineSpaces = Regex("[ \t ]+")

    fun cleanup(raw: String): String =
        raw
            .replace("\r", "")
            .split('\n')
            .map { inlineSpaces.replace(it, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .take(MAX_CHARS)

    fun anonymize(
        raw: String,
        knownNames: List<String> = emptyList(),
    ): Anonymization {
        val counter = RedactionCounter()
        val cleaned = cleanup(raw)
        if (cleaned.isEmpty()) return Anonymization("", RedactionReport.Empty)
        val header = removeHeader(cleaned, counter)
        var text = header.text
        text = redactIdentifiers(text, counter)
        text = redactNames(text, counter)
        text = redactTokens(text, header.tokens + knownNameTokens(knownNames), counter)
        text = redactPersonalLines(text, counter)
        return Anonymization(text.take(MAX_CHARS), counter.report())
    }

    private class HeaderResult(
        val text: String,
        val tokens: List<String>,
    )

    private fun removeHeader(
        text: String,
        counter: RedactionCounter,
    ): HeaderResult {
        val lines = text.split('\n')
        if (lines.any { it == HEADER_PLACEHOLDER }) return HeaderResult(text, emptyList())
        val tableStart = lines.indexOfFirst { StatementPatterns.tableHeader.containsMatchIn(it) }
        val headerEnd = if (tableStart > 0) tableStart else minOf(lines.size, HEADER_SCAN_LINES)
        val tokens = LinkedHashSet<String>()
        val output = ArrayList<String>(lines.size)
        var placeholderWritten = false
        lines.forEachIndexed { index, line ->
            val kept =
                index >= headerEnd ||
                    (
                        StatementPatterns.keptHeaderLine.containsMatchIn(line) &&
                            !StatementPatterns.trailingUpperWords.containsMatchIn(line)
                    )
            if (kept) {
                output += line
                return@forEachIndexed
            }
            StatementPatterns.upperWord
                .findAll(line)
                .map { it.value }
                .filterTo(tokens) { it !in StatementPatterns.bankVocabulary }
            counter.add(PiiKind.OTHER)
            if (!placeholderWritten) {
                output += HEADER_PLACEHOLDER
                placeholderWritten = true
            }
        }
        return HeaderResult(output.joinToString("\n"), tokens.toList())
    }

    private fun redactIdentifiers(
        input: String,
        counter: RedactionCounter,
    ): String {
        var text = input
        text = counter.replace(text, StatementPatterns.iban, PiiKind.IBAN, "[IBAN]")
        text = counter.replace(text, StatementPatterns.compactIban, PiiKind.IBAN, "[IBAN]")
        text = counter.replace(text, StatementPatterns.bic, PiiKind.BIC, "BIC : [BIC]")
        text = counter.replace(text, StatementPatterns.rib, PiiKind.ACCOUNT_NUMBER, "RIB : [RIB]")
        text = counter.replace(text, StatementPatterns.maskedCard, PiiKind.CARD_NUMBER, "[CARTE]")
        text = counter.replace(text, StatementPatterns.cardNumber, PiiKind.CARD_NUMBER, "[CARTE]")
        text = counter.replace(text, StatementPatterns.longNumber, PiiKind.ACCOUNT_NUMBER, "[NUMERO]")
        text = counter.replace(text, StatementPatterns.email, PiiKind.EMAIL, "[EMAIL]")
        text = counter.replace(text, StatementPatterns.internationalPhone, PiiKind.PHONE, "[TEL]")
        text = counter.replace(text, StatementPatterns.labelledPhone, PiiKind.PHONE, "Tél. : [TEL]")
        text = counter.replace(text, StatementPatterns.nationalPhone, PiiKind.PHONE, "[TEL]")
        text = counter.replace(text, StatementPatterns.postcodeCity, PiiKind.POSTAL_ADDRESS, "[VILLE]")
        text = counter.replace(text, StatementPatterns.address, PiiKind.POSTAL_ADDRESS, "[ADRESSE]")
        return text
    }

    private fun redactNames(
        input: String,
        counter: RedactionCounter,
    ): String {
        var text = input
        text =
            StatementPatterns.taggedName.replace(text) { match ->
                counter.add(PiiKind.PERSON_NAME)
                match.groupValues[1] + "[NOM]"
            }
        text = counter.replace(text, StatementPatterns.civilityName, PiiKind.PERSON_NAME, "[NOM]")
        text = replaceUnlessBankWords(text, StatementPatterns.upperNameLine, counter)
        text = replaceUnlessBankWords(text, StatementPatterns.upperWordsBeforeSlash, counter)
        return text
    }

    private fun replaceUnlessBankWords(
        input: String,
        pattern: Regex,
        counter: RedactionCounter,
    ): String =
        pattern.replace(input) { match ->
            val words = StatementPatterns.whitespaceRun.split(match.value)
            if (words.any { it in StatementPatterns.bankVocabulary }) {
                match.value
            } else {
                counter.add(PiiKind.PERSON_NAME)
                "[NOM]"
            }
        }

    private fun knownNameTokens(names: List<String>): List<String> =
        names
            .flatMap { name -> StatementPatterns.whitespaceRun.split(name.trim()) }
            .flatMap { word -> listOf(word) + word.split('-') }
            .map { it.trim() }
            .filter { it.length >= MIN_TOKEN_LENGTH }

    private fun redactTokens(
        input: String,
        tokens: List<String>,
        counter: RedactionCounter,
    ): String {
        val unique = LinkedHashMap<String, String>()
        tokens.filter { it.length >= MIN_TOKEN_LENGTH }.forEach { unique.getOrPut(it.lowercase()) { it } }
        if (unique.isEmpty()) return input
        var text = input
        for (token in unique.values) {
            val pattern = StatementPatterns.wordToken(token)
            text = outsidePlaceholders(text) { segment -> counter.replace(segment, pattern, PiiKind.PERSON_NAME, "[NOM]") }
        }
        return text
    }

    private inline fun outsidePlaceholders(
        text: String,
        transform: (String) -> String,
    ): String {
        val result = StringBuilder(text.length)
        var cursor = 0
        for (match in StatementPatterns.placeholder.findAll(text)) {
            result.append(transform(text.substring(cursor, match.range.first)))
            result.append(match.value)
            cursor = match.range.last + 1
        }
        result.append(transform(text.substring(cursor)))
        return result.toString()
    }

    private fun redactPersonalLines(
        input: String,
        counter: RedactionCounter,
    ): String =
        input.split('\n').joinToString("\n") { line ->
            if (StatementPatterns.personalLine.containsMatchIn(line)) {
                counter.add(PiiKind.OTHER)
                LINE_PLACEHOLDER
            } else {
                line
            }
        }
}

internal class RedactionCounter {
    private val counts = LinkedHashMap<PiiKind, Int>()

    fun add(kind: PiiKind) {
        counts[kind] = (counts[kind] ?: 0) + 1
    }

    fun replace(
        text: String,
        pattern: Regex,
        kind: PiiKind,
        replacement: String,
    ): String =
        pattern.replace(text) {
            add(kind)
            replacement
        }

    fun report(): RedactionReport = if (counts.isEmpty()) RedactionReport.Empty else RedactionReport(counts.toMap())
}
