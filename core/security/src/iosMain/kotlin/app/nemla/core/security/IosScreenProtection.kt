@file:OptIn(ExperimentalForeignApi::class)

package app.nemla.core.security

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.systemBackgroundColor
import platform.darwin.NSObjectProtocol

class IosScreenProtection(
    initiallyEnabled: Boolean,
) : ScreenProtection {
    private val observers = mutableListOf<NSObjectProtocol>()
    private val veils = mutableListOf<UIView>()

    override var isEnabled: Boolean = initiallyEnabled
        private set

    fun install() {
        if (observers.isNotEmpty()) return
        val center = NSNotificationCenter.defaultCenter
        observers +=
            center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { _ -> showVeil() }
        observers +=
            center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { _ -> hideVeil() }
    }

    fun uninstall() {
        val center = NSNotificationCenter.defaultCenter
        observers.forEach { center.removeObserver(it) }
        observers.clear()
        hideVeil()
    }

    override fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (!enabled) hideVeil()
    }

    private fun showVeil() {
        if (!isEnabled || veils.isNotEmpty()) return
        windows().forEach { window ->
            val veil = UIView(frame = window.bounds)
            veil.backgroundColor = UIColor.systemBackgroundColor
            veil.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            veil.userInteractionEnabled = false
            window.addSubview(veil)
            veils += veil
        }
    }

    private fun hideVeil() {
        veils.forEach { it.removeFromSuperview() }
        veils.clear()
    }

    private fun windows(): List<UIWindow> =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
}
