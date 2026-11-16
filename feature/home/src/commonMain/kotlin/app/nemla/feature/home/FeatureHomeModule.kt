package app.nemla.feature.home

import app.nemla.core.domain.usecase.SignOut
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val featureHomeModule: Module =
    module {
        factoryOf(::SignOut)
        factoryOf(::LoadGreetingName)
        viewModelOf(::HomeViewModel)
    }
