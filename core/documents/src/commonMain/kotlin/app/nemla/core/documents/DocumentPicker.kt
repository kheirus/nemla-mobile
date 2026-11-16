package app.nemla.core.documents

import androidx.compose.runtime.Composable

@Composable
expect fun rememberDocumentPicker(onResult: (PickedDocument?) -> Unit): () -> Unit
