package app.nemla.core.security

import org.koin.core.module.Module
import org.koin.dsl.module

val coreSecurityModule: Module =
    module {
        includes(platformSecurityModule)
    }

internal expect val platformSecurityModule: Module
