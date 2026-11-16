package app.nemla.shared

import app.nemla.ai.local.aiLocalModule
import app.nemla.ai.remote.nemla.aiRemoteNemlaModule
import app.nemla.core.audio.coreAudioModule
import app.nemla.core.common.AppBuildInfo
import app.nemla.core.common.coreCommonModule
import app.nemla.core.data.coreDataModule
import app.nemla.core.domain.port.DeviceLanguage
import app.nemla.core.domain.usecase.LoadPreferences
import app.nemla.core.domain.usecase.ObserveAuthState
import app.nemla.core.domain.usecase.ObservePreferences
import app.nemla.core.domain.usecase.ResolveStartDestination
import app.nemla.core.network.BackendConfig
import app.nemla.core.network.coreNetworkModule
import app.nemla.core.security.coreSecurityModule
import app.nemla.feature.auth.featureAuthModule
import app.nemla.feature.dashboard.featureDashboardModule
import app.nemla.feature.onboarding.featureOnboardingModule
import app.nemla.feature.settings.featureSettingsModule
import app.nemla.feature.voice.featureVoiceModule
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

internal val appModule: Module =
    module {
        factoryOf(::ObserveAuthState)
        factoryOf(::ResolveStartDestination)
        factoryOf(::ObservePreferences)
        factoryOf(::LoadPreferences)
        single<DeviceLanguage> { ComposeDeviceLanguage }
        viewModelOf(::AppViewModel)
    }

internal fun nemlaModules(
    config: BackendConfig,
    buildInfo: AppBuildInfo,
    platformModule: Module,
): List<Module> =
    listOf(
        module {
            single { config }
            single { buildInfo }
        },
        platformModule,
        coreCommonModule,
        coreSecurityModule,
        coreNetworkModule,
        coreAudioModule,
        aiLocalModule,
        aiRemoteNemlaModule,
        coreDataModule,
        featureAuthModule,
        featureDashboardModule,
        featureVoiceModule,
        featureOnboardingModule,
        featureSettingsModule,
        appModule,
    )

internal fun initKoin(
    config: BackendConfig,
    buildInfo: AppBuildInfo,
    platformModule: Module = module { },
): Koin {
    KoinPlatform.getKoinOrNull()?.let { return it }
    return startKoin { modules(nemlaModules(config, buildInfo, platformModule)) }.koin
}
