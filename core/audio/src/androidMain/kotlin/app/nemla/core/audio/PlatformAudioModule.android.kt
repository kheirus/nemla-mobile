package app.nemla.core.audio

import android.content.Context
import app.nemla.core.common.AppDispatchers
import org.koin.core.module.Module
import org.koin.dsl.binds
import org.koin.dsl.module

internal actual val platformAudioModule: Module =
    module {
        single { AndroidMicrophone(get<Context>(), get<AppDispatchers>().io) } binds
            arrayOf(AudioSource::class, MicrophonePermission::class)
    }
