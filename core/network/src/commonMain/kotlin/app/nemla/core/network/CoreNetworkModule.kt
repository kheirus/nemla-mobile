package app.nemla.core.network

import app.nemla.core.common.AppBuildInfo
import org.koin.core.module.Module
import org.koin.dsl.module

val coreNetworkModule: Module =
    module {
        single<TokenProvider> {
            val scope = this
            createTokenProvider(store = lazy { scope.get<SessionTokenStore>() }, logger = get())
        }
        single {
            createHttpClient(
                config = get(),
                tokenProvider = get(),
                logger = get(),
                enableRequestLog = get<AppBuildInfo>().isDebug,
            )
        }
    }
