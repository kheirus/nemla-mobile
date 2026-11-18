package app.nemla.core.notifications

import androidx.compose.runtime.Composable

@Composable
expect fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit
