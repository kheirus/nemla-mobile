package app.nemla.core.documents

import app.nemla.core.common.AppLogger
import app.nemla.core.common.LogAttribute
import app.nemla.core.common.LogEvent
import app.nemla.core.common.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DocumentTextReaderTest {
    private val statement =
        "Relevé de compte\r\nDate;Libellé;Débit\r\n\r\n02/03/2026;CB\tBOULANGERIE   TEST;3,80\r\n" +
            "  05/03/2026;PRLV  OPERATEUR;19,99  \r\n\r\n\r\n"

    private class FakeSource(
        override val name: String,
        override val mimeType: String? = null,
        private val size: Long? = null,
        private val bytes: ByteRead = ByteRead.Unreadable,
        private val pdf: PdfText = PdfText.Unreadable,
    ) : DocumentSource {
        var byteReads = 0
        var pdfReads = 0

        override suspend fun sizeBytes(): Long? = size

        override suspend fun readBytes(maxBytes: Int): ByteRead {
            byteReads++
            return bytes
        }

        override suspend fun pdfText(maxBytes: Int): PdfText {
            pdfReads++
            return pdf
        }
    }

    private class RecordingLogger : AppLogger {
        val lines = mutableListOf<String>()

        override fun <E> log(
            level: LogLevel,
            event: E,
            attributes: List<LogAttribute>,
        ) where E : Enum<E>, E : LogEvent {
            lines += "${event.name} ${attributes.joinToString(" ")}"
        }
    }

    private val logger = RecordingLogger()

    private fun reader(): DefaultDocumentTextReader = DefaultDocumentTextReader(Dispatchers.Unconfined, logger)

    private fun text(
        content: String,
        name: String = "releve.csv",
    ): FakeSource = FakeSource(name, bytes = ByteRead.Bytes(content.encodeToByteArray()))

    private suspend fun read(source: FakeSource): DocumentReadResult = reader().read(SourcedDocument(source))

    private fun DocumentReadResult.text(): DocumentText = assertIs<DocumentReadResult.Success>(this).document

    private fun DocumentReadResult.error(): DocumentError = assertIs<DocumentReadResult.Failure>(this).error

    @Test
    fun csvIsDecodedAndCleanedLikeTheServer() =
        runTest {
            val document = read(text(statement)).text()
            assertEquals(DocumentKind.CSV, document.kind)
            assertEquals(
                "Relevé de compte\nDate;Libellé;Débit\n02/03/2026;CB BOULANGERIE TEST;3,80\n05/03/2026;PRLV OPERATEUR;19,99",
                document.text,
            )
        }

    @Test
    fun plainTextLosesItsByteOrderMarkAndInvalidBytesAreReplaced() =
        runTest {
            val bytes =
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Date Libellé Montant ".encodeToByteArray() +
                    byteArrayOf(0xFF.toByte()) + " 12,30 CB TEST".encodeToByteArray()
            val document = read(FakeSource("export.txt", bytes = ByteRead.Bytes(bytes))).text()
            assertEquals(DocumentKind.TEXT, document.kind)
            assertEquals("Date Libellé Montant � 12,30 CB TEST", document.text)
        }

    @Test
    fun bytesAreWipedOnceDecoded() =
        runTest {
            val bytes = statement.encodeToByteArray()
            read(FakeSource("releve.csv", bytes = ByteRead.Bytes(bytes))).text()
            assertTrue(bytes.all { it == 0.toByte() })
        }

    @Test
    fun outputIsTruncatedToNinetyThousandCharacters() =
        runTest {
            val line = "02/03/2026;CB TEST;12,30\n"
            val document = read(text(line.repeat(DocumentRules.MAX_CHARS / line.length + 50))).text()
            assertEquals(DocumentRules.MAX_CHARS, document.text.length)
        }

    @Test
    fun fewerThanTwentyReadableCharactersLookScannedOrEmpty() =
        runTest {
            assertEquals(DocumentError.EMPTY_OR_SCANNED, read(text("  \r\n \t 12,30 CB  \n\n")).error())
            assertEquals(DocumentError.EMPTY_OR_SCANNED, read(text("")).error())
            val pdf = FakeSource("scan.pdf", pdf = PdfText.Pages(listOf("   ", "\n")))
            assertEquals(DocumentError.EMPTY_OR_SCANNED, read(pdf).error())
            assertIs<DocumentReadResult.Success>(read(text("12345678901234567890")))
        }

    @Test
    fun filesOverEightMegabytesAreRefusedBeforeReading() =
        runTest {
            val declared =
                FakeSource("releve.csv", size = DocumentRules.MAX_BYTES + 1L, bytes = ByteRead.Bytes(statement.encodeToByteArray()))
            assertEquals(DocumentError.TOO_LARGE, read(declared).error())
            assertEquals(0, declared.byteReads)
            val pdf = FakeSource("releve.pdf", size = DocumentRules.MAX_BYTES + 1L, pdf = PdfText.Pages(listOf(statement)))
            assertEquals(DocumentError.TOO_LARGE, read(pdf).error())
            assertEquals(0, pdf.pdfReads)
            val exact =
                FakeSource("releve.csv", size = DocumentRules.MAX_BYTES.toLong(), bytes = ByteRead.Bytes(statement.encodeToByteArray()))
            assertIs<DocumentReadResult.Success>(read(exact))
        }

    @Test
    fun oversizedContentIsRefusedWhenTheSizeIsUnknown() =
        runTest {
            assertEquals(DocumentError.TOO_LARGE, read(FakeSource("releve.csv", bytes = ByteRead.TooLarge)).error())
            assertEquals(
                DocumentError.TOO_LARGE,
                read(
                    FakeSource("releve.csv", bytes = ByteRead.Bytes(ByteArray(DocumentRules.MAX_BYTES + 1) { 'a'.code.toByte() })),
                ).error(),
            )
            assertEquals(DocumentError.TOO_LARGE, read(FakeSource("releve.pdf", pdf = PdfText.TooLarge)).error())
        }

    @Test
    fun pdfPagesAreTrimmedAndJoinedByABlankLineThenCleaned() =
        runTest {
            val source = FakeSource("Releve.PDF", pdf = PdfText.Pages(listOf("  Relevé de compte mars  ", "02/03 CB TEST 3,80\n")))
            val document = read(source).text()
            assertEquals(DocumentKind.PDF, document.kind)
            assertEquals("Relevé de compte mars\n02/03 CB TEST 3,80", document.text)
            assertEquals(0, source.byteReads)
        }

    @Test
    fun pdfWithoutTextApiOnThisDeviceIsReported() =
        runTest {
            assertEquals(DocumentError.PDF_UNSUPPORTED_ON_DEVICE, read(FakeSource("releve.pdf", pdf = PdfText.Unsupported)).error())
            assertEquals(DocumentError.UNREADABLE, read(FakeSource("releve.pdf", pdf = PdfText.Unreadable)).error())
        }

    @Test
    fun spreadsheetsAreNotReadAndAskForCsv() =
        runTest {
            for (name in listOf("releve.xlsx", "releve.XLS", "releve.xlsm", "releve.ods")) {
                val source = FakeSource(name, bytes = ByteRead.Bytes(statement.encodeToByteArray()))
                assertEquals(DocumentError.SPREADSHEET_UNSUPPORTED, read(source).error(), name)
                assertEquals(0, source.byteReads)
            }
            val byMime = FakeSource("releve", mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            assertEquals(DocumentError.SPREADSHEET_UNSUPPORTED, read(byMime).error())
        }

    @Test
    fun kindFollowsTheExtensionThenTheMimeType() {
        assertEquals(DocumentKind.PDF, DocumentRules.kindOf("document", "application/pdf"))
        assertEquals(DocumentKind.CSV, DocumentRules.kindOf("export.CSV", "text/plain"))
        assertEquals(DocumentKind.TEXT, DocumentRules.kindOf("export", "text/csv"))
        assertEquals(DocumentKind.TEXT, DocumentRules.kindOf("notes.txt", null))
        assertEquals(DocumentKind.SPREADSHEET, DocumentRules.kindOf("export", "application/vnd.ms-excel; charset=binary"))
    }

    @Test
    fun unreadableSourcesAndForeignDocumentsFailCleanly() =
        runTest {
            assertEquals(DocumentError.UNREADABLE, read(FakeSource("releve.csv", bytes = ByteRead.Unreadable)).error())
            val throwing =
                object : DocumentSource {
                    override val name = "releve.csv"
                    override val mimeType: String? = null

                    override suspend fun sizeBytes(): Long? = null

                    override suspend fun readBytes(maxBytes: Int): ByteRead = throw IllegalStateException("revoked")

                    override suspend fun pdfText(maxBytes: Int): PdfText = PdfText.Unreadable
                }
            assertEquals(DocumentError.UNREADABLE, reader().read(SourcedDocument(throwing)).error())
            val foreign =
                object : PickedDocument {
                    override val displayName = "releve.csv"
                }
            assertEquals(DocumentError.UNREADABLE, reader().read(foreign).error())
        }

    @Test
    fun namesAndContentNeverReachLogsOrToString() =
        runTest {
            val source = text(statement, name = "releve-camille-testeur.csv")
            val document = read(source).text()
            read(FakeSource("camille.xlsx"))
            val picked = SourcedDocument(source)
            assertFalse(picked.toString().contains("camille"))
            assertFalse(document.toString().contains("BOULANGERIE"))
            assertTrue(logger.lines.isNotEmpty())
            assertTrue(logger.lines.none { it.contains("camille") || it.contains("BOULANGERIE") })
            assertEquals("releve-camille-testeur.csv", picked.displayName)
        }

    @Test
    fun koinModuleProvidesTheReader() {
        val koin =
            koinApplication {
                modules(
                    module {
                        single<app.nemla.core.common.AppDispatchers> { app.nemla.core.common.DefaultAppDispatchers }
                        single<AppLogger> { this@DocumentTextReaderTest.logger }
                    },
                    coreDocumentsModule,
                )
            }.koin
        assertIs<DefaultDocumentTextReader>(koin.get<DocumentTextReader>())
    }
}
