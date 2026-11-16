package app.nemla.core.audio

import app.nemla.core.common.AppDispatchers
import org.koin.core.module.Module
import org.koin.dsl.module

val coreAudioModule: Module =
    module {
        includes(platformAudioModule)
        single<VoiceRecorderFactory> {
            DefaultVoiceRecorderFactory(
                source = get(),
                permission = get(),
                encoding = get<AppDispatchers>().default,
                logger = get(),
            )
        }
    }

internal expect val platformAudioModule: Module
