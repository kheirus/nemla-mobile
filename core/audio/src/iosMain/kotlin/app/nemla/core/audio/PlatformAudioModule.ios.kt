package app.nemla.core.audio

import org.koin.core.module.Module
import org.koin.dsl.binds
import org.koin.dsl.module

internal actual val platformAudioModule: Module =
    module {
        single { IosMicrophone() } binds arrayOf(AudioSource::class, MicrophonePermission::class)
    }
