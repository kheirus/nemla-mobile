package app.nemla.core.documents

enum class DocumentKind { PDF, CSV, TEXT, SPREADSHEET }

interface PickedDocument {
    val displayName: String
}

class DocumentText(
    val text: String,
    val kind: DocumentKind,
) {
    override fun equals(other: Any?): Boolean = other is DocumentText && other.text == text && other.kind == kind

    override fun hashCode(): Int = listOf(text, kind).hashCode()

    override fun toString(): String = "DocumentText(kind=$kind, length=${text.length})"
}

enum class DocumentError {
    TOO_LARGE,
    SPREADSHEET_UNSUPPORTED,
    PDF_UNSUPPORTED_ON_DEVICE,
    EMPTY_OR_SCANNED,
    UNREADABLE,
}

sealed interface DocumentReadResult {
    data class Success(
        val document: DocumentText,
    ) : DocumentReadResult

    data class Failure(
        val error: DocumentError,
    ) : DocumentReadResult
}

interface DocumentTextReader {
    suspend fun read(document: PickedDocument): DocumentReadResult
}
