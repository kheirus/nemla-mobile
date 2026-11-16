package app.nemla.core.documents

internal sealed interface ByteRead {
    class Bytes(
        val bytes: ByteArray,
    ) : ByteRead {
        override fun toString(): String = "Bytes(${bytes.size})"
    }

    data object TooLarge : ByteRead

    data object Unreadable : ByteRead
}

internal sealed interface PdfText {
    class Pages(
        val pages: List<String>,
    ) : PdfText {
        override fun toString(): String = "Pages(${pages.size})"
    }

    data object TooLarge : PdfText

    data object Unsupported : PdfText

    data object Unreadable : PdfText
}

internal interface DocumentSource {
    val name: String
    val mimeType: String?

    suspend fun sizeBytes(): Long?

    suspend fun readBytes(maxBytes: Int): ByteRead

    suspend fun pdfText(maxBytes: Int): PdfText
}

internal class SourcedDocument(
    val source: DocumentSource,
) : PickedDocument {
    override val displayName: String get() = source.name

    override fun toString(): String = "PickedDocument(kind=${DocumentRules.kindOf(source.name, source.mimeType)})"
}
