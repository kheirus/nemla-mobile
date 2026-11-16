package app.nemla.core.documents

import app.nemla.core.common.AppLogger
import app.nemla.core.common.LogAttribute
import app.nemla.core.common.LogEvent
import app.nemla.core.common.LogField
import app.nemla.core.common.info
import app.nemla.core.common.warn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

internal enum class DocumentLog : LogEvent {
    DOCUMENT_READ,
    DOCUMENT_REJECTED,
}

internal class DefaultDocumentTextReader(
    private val dispatcher: CoroutineDispatcher,
    private val logger: AppLogger,
) : DocumentTextReader {
    override suspend fun read(document: PickedDocument): DocumentReadResult {
        val source = (document as? SourcedDocument)?.source ?: return reject(DocumentError.UNREADABLE)
        val result =
            withContext(dispatcher) {
                try {
                    readSource(source)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    DocumentReadResult.Failure(DocumentError.UNREADABLE)
                }
            }
        when (result) {
            is DocumentReadResult.Success -> {
                logger.info(
                    DocumentLog.DOCUMENT_READ,
                    LogAttribute.choice(LogField.KIND, result.document.kind),
                    LogAttribute.number(LogField.COUNT, result.document.text.length),
                )
            }

            is DocumentReadResult.Failure -> {
                logger.warn(DocumentLog.DOCUMENT_REJECTED, LogAttribute.choice(LogField.REASON, result.error))
            }
        }
        return result
    }

    private fun reject(error: DocumentError): DocumentReadResult {
        logger.warn(DocumentLog.DOCUMENT_REJECTED, LogAttribute.choice(LogField.REASON, error))
        return DocumentReadResult.Failure(error)
    }

    private suspend fun readSource(source: DocumentSource): DocumentReadResult {
        val kind = DocumentRules.kindOf(source.name, source.mimeType)
        if (kind == DocumentKind.SPREADSHEET) return DocumentReadResult.Failure(DocumentError.SPREADSHEET_UNSUPPORTED)
        val size = source.sizeBytes()
        if (size != null && size > DocumentRules.MAX_BYTES) return DocumentReadResult.Failure(DocumentError.TOO_LARGE)
        val raw =
            when (kind) {
                DocumentKind.PDF -> {
                    when (val pdf = source.pdfText(DocumentRules.MAX_BYTES)) {
                        is PdfText.Pages -> DocumentRules.joinPages(pdf.pages)
                        PdfText.TooLarge -> return DocumentReadResult.Failure(DocumentError.TOO_LARGE)
                        PdfText.Unsupported -> return DocumentReadResult.Failure(DocumentError.PDF_UNSUPPORTED_ON_DEVICE)
                        PdfText.Unreadable -> return DocumentReadResult.Failure(DocumentError.UNREADABLE)
                    }
                }

                else -> {
                    when (val read = source.readBytes(DocumentRules.MAX_BYTES)) {
                        is ByteRead.Bytes -> decodeAndWipe(read.bytes) ?: return DocumentReadResult.Failure(DocumentError.TOO_LARGE)
                        ByteRead.TooLarge -> return DocumentReadResult.Failure(DocumentError.TOO_LARGE)
                        ByteRead.Unreadable -> return DocumentReadResult.Failure(DocumentError.UNREADABLE)
                    }
                }
            }
        val text = DocumentRules.cleanup(raw)
        if (text.trim().length < DocumentRules.MIN_CHARS) return DocumentReadResult.Failure(DocumentError.EMPTY_OR_SCANNED)
        return DocumentReadResult.Success(DocumentText(text, kind))
    }

    private fun decodeAndWipe(bytes: ByteArray): String? =
        try {
            if (bytes.size > DocumentRules.MAX_BYTES) null else DocumentRules.decode(bytes)
        } finally {
            bytes.fill(0)
        }
}
