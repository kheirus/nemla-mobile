package app.nemla.core.documents

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberDocumentPicker(onResult: (PickedDocument?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val resolver = remember(context) { context.applicationContext.contentResolver }
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            currentOnResult(uri?.let { SourcedDocument(describe(resolver, it)) })
        }
    return remember(launcher) { { launcher.launch(DocumentRules.pickerMimeTypes.toTypedArray()) } }
}

private const val FALLBACK_NAME = "document"

private fun describe(
    resolver: ContentResolver,
    uri: Uri,
): AndroidDocumentSource {
    var name: String? = null
    var size: Long? = null
    try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
    } catch (e: RuntimeException) {
        name = null
    }
    val mimeType =
        try {
            resolver.getType(uri)
        } catch (e: RuntimeException) {
            null
        }
    return AndroidDocumentSource(resolver, uri, name?.takeIf { it.isNotBlank() } ?: FALLBACK_NAME, mimeType, size)
}
