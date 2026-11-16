package app.nemla.core.documents

import android.annotation.SuppressLint
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfRendererPreV
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import java.io.IOException

internal object AndroidPdfText {
    private const val PDF_EXTENSION_VERSION = 13

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM || hasPdfExtension()

    @SuppressLint("NewApi")
    fun extract(descriptor: ParcelFileDescriptor): PdfText =
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> PdfText.Pages(readWithRenderer(descriptor))
                hasPdfExtension() -> PdfText.Pages(readWithPreV(descriptor))
                else -> PdfText.Unsupported.also { descriptor.close() }
            }
        } catch (e: IOException) {
            PdfText.Unreadable
        } catch (e: SecurityException) {
            PdfText.Unreadable
        } catch (e: IllegalStateException) {
            PdfText.Unreadable
        }

    private fun hasPdfExtension(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= PDF_EXTENSION_VERSION

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun readWithRenderer(descriptor: ParcelFileDescriptor): List<String> =
        PdfRenderer(descriptor).use { renderer ->
            (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { page -> page.textContents.joinToString("\n") { it.text } }
            }
        }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = PDF_EXTENSION_VERSION)
    private fun readWithPreV(descriptor: ParcelFileDescriptor): List<String> =
        PdfRendererPreV(descriptor).use { renderer ->
            (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { page -> page.textContents.joinToString("\n") { it.text } }
            }
        }
}
