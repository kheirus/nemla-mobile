package app.nemla.core.security

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import java.util.WeakHashMap

class AndroidScreenProtection(
    initiallyEnabled: Boolean,
) : ScreenProtection {
    private val activities = WeakHashMap<Activity, Unit>()
    private var installedOn: Application? = null

    @Volatile
    override var isEnabled: Boolean = initiallyEnabled
        private set

    fun install(application: Application) {
        if (installedOn != null) return
        installedOn = application
        application.registerActivityLifecycleCallbacks(callbacks)
    }

    fun protect(activity: Activity) {
        activities[activity] = Unit
        applyTo(activity)
    }

    override fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        activities.keys.toList().forEach(::applyTo)
    }

    private fun applyTo(activity: Activity) {
        if (isEnabled) {
            activity.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private val callbacks =
        object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(
                activity: Activity,
                savedInstanceState: Bundle?,
            ) = protect(activity)

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) = Unit

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(
                activity: Activity,
                outState: Bundle,
            ) = Unit

            override fun onActivityDestroyed(activity: Activity) {
                activities.remove(activity)
            }
        }
}
