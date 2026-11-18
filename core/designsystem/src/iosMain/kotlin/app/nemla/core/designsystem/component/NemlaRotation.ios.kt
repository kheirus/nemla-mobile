package app.nemla.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.attemptRotationToDeviceOrientation
import platform.UIKit.setNeedsUpdateOfSupportedInterfaceOrientations

object NemlaOrientationLock {
    var landscapeAllowed: Boolean = false
        private set

    @OptIn(ExperimentalForeignApi::class)
    internal fun update(allowed: Boolean) {
        landscapeAllowed = allowed
        val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
        scenes.forEach { scene ->
            scene.windows.forEach { window ->
                val root = (window as? UIWindow)?.rootViewController ?: return@forEach
                if (root.respondsToSelector(NSSelectorFromString("setNeedsUpdateOfSupportedInterfaceOrientations"))) {
                    root.setNeedsUpdateOfSupportedInterfaceOrientations()
                } else {
                    UIViewController.attemptRotationToDeviceOrientation()
                }
            }
            if (!allowed && scene.respondsToSelector(NSSelectorFromString("requestGeometryUpdateWithPreferences:errorHandler:"))) {
                scene.requestGeometryUpdateWithPreferences(
                    UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = UIInterfaceOrientationMaskPortrait),
                    errorHandler = null,
                )
            }
        }
    }
}

@Composable
actual fun NemlaRotationAllowed() {
    DisposableEffect(Unit) {
        NemlaOrientationLock.update(true)
        onDispose { NemlaOrientationLock.update(false) }
    }
}
