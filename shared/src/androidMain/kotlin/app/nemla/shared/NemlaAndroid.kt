package app.nemla.shared

import android.app.Application
import android.content.Context
import app.nemla.core.common.AppBuildInfo
import app.nemla.core.network.BackendConfig
import app.nemla.core.security.AndroidScreenProtection
import org.koin.dsl.module

object NemlaAndroid {
    fun start(
        application: Application,
        supabaseUrl: String,
        supabasePublishableKey: String,
        apiBaseUrl: String,
        isDebug: Boolean,
    ) {
        val koin =
            initKoin(
                config = BackendConfig(supabaseUrl, supabasePublishableKey, apiBaseUrl),
                buildInfo = AppBuildInfo(isDebug),
                platformModule = module { single<Context> { application } },
            )
        koin.get<AndroidScreenProtection>().install(application)
    }
}
