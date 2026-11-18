package app.nemla.core.notifications

import org.koin.core.module.Module
import org.koin.dsl.module

val coreNotificationsModule: Module =
    module {
        includes(platformNotificationsModule)
    }

internal expect val platformNotificationsModule: Module
