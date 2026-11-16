package app.nemla.core.documents

internal object DocumentRules {
    const val MAX_BYTES: Int = 8 * 1024 * 1024
    const val MAX_CHARS: Int = 90_000
    const val MIN_CHARS: Int = 20
    private const val BYTE_ORDER_MARK = "﻿"
    private const val PAGE_SEPARATOR = "\n\n"
    private val inlineSpaces = Regex("[ \t ]+")
    private val spreadsheetExtensions = setOf("xls", "xlsx", "xlsm", "ods")
    private val spreadsheetMimeTypes =
        setOf(
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel.sheet.macroenabled.12",
            "application/vnd.oasis.opendocument.spreadsheet",
        )

    val pickerMimeTypes: List<String> =
        listOf(
            "application/pdf",
            "text/csv",
            "text/comma-separated-values",
            "text/plain",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel.sheet.macroEnabled.12",
            "application/vnd.oasis.opendocument.spreadsheet",
        )

    fun kindOf(
        name: String,
        mimeType: String?,
    ): DocumentKind {
        val extension = name.substringAfterLast('.', "").lowercase()
        val mime = mimeType?.lowercase()?.substringBefore(';')?.trim()
        return when {
            extension == "pdf" || mime == "application/pdf" -> DocumentKind.PDF
            extension in spreadsheetExtensions || mime in spreadsheetMimeTypes -> DocumentKind.SPREADSHEET
            extension == "csv" -> DocumentKind.CSV
            else -> DocumentKind.TEXT
        }
    }

    fun decode(bytes: ByteArray): String = bytes.decodeToString().removePrefix(BYTE_ORDER_MARK)

    fun joinPages(pages: List<String>): String = pages.joinToString(PAGE_SEPARATOR) { it.trim() }

    fun cleanup(raw: String): String =
        raw
            .replace("\r", "")
            .split('\n')
            .map { inlineSpaces.replace(it, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .take(MAX_CHARS)
}
