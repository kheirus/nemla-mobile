package app.nemla.core.documents

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.IOException
import java.io.InputStream

internal class AndroidDocumentSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
    override val name: String,
    override val mimeType: String?,
    private val declaredSize: Long?,
) : DocumentSource {
    override suspend fun sizeBytes(): Long? = declaredSize?.takeIf { it >= 0 }

    override suspend fun readBytes(maxBytes: Int): ByteRead =
        try {
            resolver.openInputStream(uri)?.use { readCapped(it, maxBytes) } ?: ByteRead.Unreadable
        } catch (e: IOException) {
            ByteRead.Unreadable
        } catch (e: SecurityException) {
            ByteRead.Unreadable
        }

    override suspend fun pdfText(maxBytes: Int): PdfText {
        if (!AndroidPdfText.isSupported()) return PdfText.Unsupported
        val descriptor: ParcelFileDescriptor =
            try {
                resolver.openFileDescriptor(uri, READ_MODE)
            } catch (e: IOException) {
                null
            } catch (e: SecurityException) {
                null
            } ?: return PdfText.Unreadable
        val size = descriptor.statSize
        if (size > maxBytes) {
            descriptor.close()
            return PdfText.TooLarge
        }
        return AndroidPdfText.extract(descriptor)
    }

    override fun toString(): String = "AndroidDocumentSource"

    private fun readCapped(
        input: InputStream,
        maxBytes: Int,
    ): ByteRead {
        var buffer = ByteArray(INITIAL_BUFFER)
        var length = 0
        while (true) {
            if (length == buffer.size) {
                if (buffer.size > maxBytes) {
                    buffer.fill(0)
                    return ByteRead.TooLarge
                }
                val grown = buffer.copyOf(minOf(buffer.size * 2, maxBytes + 1))
                buffer.fill(0)
                buffer = grown
            }
            val read = input.read(buffer, length, buffer.size - length)
            if (read < 0) break
            length += read
            if (length > maxBytes) {
                buffer.fill(0)
                return ByteRead.TooLarge
            }
        }
        val bytes = buffer.copyOf(length)
        buffer.fill(0)
        return ByteRead.Bytes(bytes)
    }

    private companion object {
        const val READ_MODE = "r"
        const val INITIAL_BUFFER = 64 * 1024
    }
}
