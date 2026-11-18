package app.nemla.core.notifications

import android.content.Context
import app.nemla.core.common.AppDispatchers
import app.nemla.core.domain.port.LocalNotifier
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val platformNotificationsModule: Module =
    module {
        single<LocalNotifier> { AndroidLocalNotifier(get<Context>(), get<AppDispatchers>().io) }
    }
