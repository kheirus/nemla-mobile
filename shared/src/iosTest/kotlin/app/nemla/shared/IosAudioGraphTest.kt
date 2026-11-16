package app.nemla.shared

import app.nemla.core.audio.MicrophonePermission
import app.nemla.core.audio.VoiceRecorderFactory
import app.nemla.core.common.AppBuildInfo
import app.nemla.core.network.BackendConfig
import app.nemla.core.security.InMemorySecureStore
import app.nemla.core.security.SecureStore
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test

class IosAudioGraphTest {
    @Test
    fun microphoneAndRecorderResolveOnIos() {
        val config = BackendConfig("https://project.supabase.co", "sb_publishable_test", "https://nemla.app")
        val overrides = module { single<SecureStore> { InMemorySecureStore() } }
        val application = koinApplication { modules(nemlaModules(config, AppBuildInfo(false), module { }) + overrides) }
        try {
            application.koin.get<MicrophonePermission>()
            application.koin.get<VoiceRecorderFactory>()
        } finally {
            application.close()
        }
    }
}
