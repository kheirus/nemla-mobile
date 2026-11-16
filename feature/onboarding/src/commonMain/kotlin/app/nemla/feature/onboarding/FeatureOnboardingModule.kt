package app.nemla.feature.onboarding

import app.nemla.core.domain.usecase.CompleteOnboarding
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.GetUserProfile
import app.nemla.core.domain.usecase.ParseRecurringCharges
import app.nemla.core.domain.usecase.PrepareOnboarding
import app.nemla.core.domain.usecase.SignOut
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val featureOnboardingModule: Module =
    module {
        factoryOf(::PrepareOnboarding)
        factoryOf(::GetUserProfile)
        factoryOf(::ParseRecurringCharges)
        factoryOf(::CompleteOnboarding)
        factoryOf(::SignOut)
        factoryOf(::CurrentLanguages)
        viewModelOf(::OnboardingViewModel)
    }
