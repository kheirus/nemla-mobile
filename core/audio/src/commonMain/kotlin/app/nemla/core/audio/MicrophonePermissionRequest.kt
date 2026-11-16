package app.nemla.core.audio

import androidx.compose.runtime.Composable

@Composable
expect fun rememberMicrophonePermissionRequest(onResult: (Boolean) -> Unit): () -> Unit
