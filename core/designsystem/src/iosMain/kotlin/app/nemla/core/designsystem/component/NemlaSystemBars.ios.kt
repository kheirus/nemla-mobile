package app.nemla.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.setStatusBarStyle
import platform.UIKit.statusBarStyle

@Composable
actual fun NemlaLightStatusBarIcons() {
    DisposableEffect(Unit) {
        val application = UIApplication.sharedApplication
        val previous = application.statusBarStyle
        application.setStatusBarStyle(UIStatusBarStyleLightContent, animated = true)
        onDispose { application.setStatusBarStyle(previous, animated = true) }
    }
}
