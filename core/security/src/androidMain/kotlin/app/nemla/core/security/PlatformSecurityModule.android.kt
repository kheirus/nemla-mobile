package app.nemla.core.security

import android.content.Context
import app.nemla.core.common.AppBuildInfo
import app.nemla.core.common.AppDispatchers
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual val platformSecurityModule: Module =
    module {
        single<SecureStore> { createAndroidSecureStore(get<Context>(), get<AppDispatchers>().io, get()) }
        single { AndroidScreenProtection(initiallyEnabled = !get<AppBuildInfo>().isDebug) } bind ScreenProtection::class
    }
