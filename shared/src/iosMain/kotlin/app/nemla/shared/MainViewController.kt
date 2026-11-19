package app.nemla.shared

import androidx.compose.ui.window.ComposeUIViewController
import app.nemla.core.common.AppBuildInfo
import app.nemla.core.designsystem.component.NemlaOrientationLock
import app.nemla.core.network.BackendConfig
import app.nemla.core.security.IosScreenProtection
import platform.UIKit.UIViewController

fun startNemla(
    supabaseUrl: String,
    supabasePublishableKey: String,
    apiBaseUrl: String,
    isDebug: Boolean,
) {
    val koin =
        initKoin(
            config = BackendConfig(supabaseUrl, supabasePublishableKey, apiBaseUrl),
            buildInfo = AppBuildInfo(isDebug),
        )
    koin.get<IosScreenProtection>().install()
}

fun mainViewController(): UIViewController = ComposeUIViewController { App() }

fun landscapeAllowed(): Boolean = NemlaOrientationLock.landscapeAllowed
