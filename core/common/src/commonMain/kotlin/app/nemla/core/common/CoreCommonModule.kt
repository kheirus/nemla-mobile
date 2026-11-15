package app.nemla.core.common

import org.koin.dsl.module

val coreCommonModule =
    module {
        single<AppDispatchers> { DefaultAppDispatchers }
        single<AppLogger> { platformLogger(get()) }
    }
