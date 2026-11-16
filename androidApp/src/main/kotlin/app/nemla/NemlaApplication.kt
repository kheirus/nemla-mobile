package app.nemla

import android.app.Application
import app.nemla.shared.NemlaAndroid

class NemlaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NemlaAndroid.start(
            application = this,
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabasePublishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            apiBaseUrl = BuildConfig.API_BASE_URL,
            isDebug = BuildConfig.DEBUG,
        )
    }
}
