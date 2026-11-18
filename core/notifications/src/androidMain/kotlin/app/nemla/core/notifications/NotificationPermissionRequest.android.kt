package app.nemla.core.notifications

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.NotificationManagerCompat

@Composable
actual fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> currentOnResult(granted) }
    return remember(launcher, activity) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                currentOnResult(activity?.let { NotificationManagerCompat.from(it).areNotificationsEnabled() } ?: false)
            }
        }
    }
}
