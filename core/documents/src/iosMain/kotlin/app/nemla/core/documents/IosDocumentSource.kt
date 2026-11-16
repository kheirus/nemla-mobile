package app.nemla.core.documents

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLFileSizeKey
import platform.Foundation.dataWithContentsOfURL
import platform.PDFKit.PDFDocument
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal class IosDocumentSource(
    private val url: NSURL,
) : DocumentSource {
    override val name: String = url.lastPathComponent?.takeIf { it.isNotBlank() } ?: FALLBACK_NAME
    override val mimeType: String? = null

    override suspend fun sizeBytes(): Long? =
        withAccess {
            val values = url.resourceValuesForKeys(listOf(NSURLFileSizeKey), null)
            (values?.get(NSURLFileSizeKey) as? NSNumber)?.longLongValue
        }

    override suspend fun readBytes(maxBytes: Int): ByteRead =
        withAccess {
            val data = NSData.dataWithContentsOfURL(url) ?: return@withAccess ByteRead.Unreadable
            if (data.length > maxBytes.toULong()) ByteRead.TooLarge else ByteRead.Bytes(data.toByteArray())
        }

    override suspend fun pdfText(maxBytes: Int): PdfText =
        withAccess {
            val data = NSData.dataWithContentsOfURL(url) ?: return@withAccess PdfText.Unreadable
            if (data.length > maxBytes.toULong()) return@withAccess PdfText.TooLarge
            val document = PDFDocument(data = data)
            if (document.isLocked()) return@withAccess PdfText.Unreadable
            val count = document.pageCount.toInt()
            PdfText.Pages((0 until count).map { index -> document.pageAtIndex(index.toULong())?.string.orEmpty() })
        }

    override fun toString(): String = "IosDocumentSource"

    private inline fun <T> withAccess(block: () -> T): T {
        val started = url.startAccessingSecurityScopedResource()
        try {
            return block()
        } finally {
            if (started) url.stopAccessingSecurityScopedResource()
        }
    }

    private companion object {
        const val FALLBACK_NAME = "document"
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) {
        bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, length) }
    }
    return bytes
}
