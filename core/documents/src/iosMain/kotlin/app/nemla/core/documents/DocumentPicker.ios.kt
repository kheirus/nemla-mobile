package app.nemla.core.documents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeCommaSeparatedText
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.UniformTypeIdentifiers.UTTypeSpreadsheet
import platform.darwin.NSObject

@Composable
actual fun rememberDocumentPicker(onResult: (PickedDocument?) -> Unit): () -> Unit {
    val controller = LocalUIViewController.current
    val currentOnResult by rememberUpdatedState(onResult)
    val delegate = remember { DocumentPickerDelegate { currentOnResult(it) } }
    return remember(controller, delegate) { { presentPicker(controller, delegate) } }
}

private val spreadsheetIdentifiers =
    listOf(
        "com.microsoft.excel.xls",
        "org.openxmlformats.spreadsheetml.sheet",
        "org.openxmlformats.spreadsheetml.sheet.macroenabled",
        "org.oasis-open.opendocument.spreadsheet",
    )

private fun contentTypes(): List<UTType> =
    listOfNotNull(UTTypePDF, UTTypeCommaSeparatedText, UTTypePlainText, UTTypeSpreadsheet) +
        spreadsheetIdentifiers.mapNotNull { UTType.typeWithIdentifier(it) }

private fun presentPicker(
    controller: UIViewController,
    delegate: DocumentPickerDelegate,
) {
    val picker = UIDocumentPickerViewController(forOpeningContentTypes = contentTypes(), asCopy = false)
    picker.delegate = delegate
    picker.allowsMultipleSelection = false
    var presenter = controller
    while (true) {
        presenter = presenter.presentedViewController ?: break
    }
    presenter.presentViewController(picker, animated = true, completion = null)
}

private class DocumentPickerDelegate(
    private val onResult: (PickedDocument?) -> Unit,
) : NSObject(),
    UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        onResult(url?.let { SourcedDocument(IosDocumentSource(it)) })
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(null)
    }
}
