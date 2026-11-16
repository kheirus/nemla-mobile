package app.nemla.feature.settings

import app.nemla.core.domain.usecase.AddCategory
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.DeleteAccount
import app.nemla.core.domain.usecase.DeleteCategory
import app.nemla.core.domain.usecase.DeleteRecurringCharge
import app.nemla.core.domain.usecase.GetDebitSettings
import app.nemla.core.domain.usecase.GetMonthlyQuota
import app.nemla.core.domain.usecase.GetUserProfile
import app.nemla.core.domain.usecase.ListCategories
import app.nemla.core.domain.usecase.ListRecurringCharges
import app.nemla.core.domain.usecase.ObserveAiConsents
import app.nemla.core.domain.usecase.ObservePreferences
import app.nemla.core.domain.usecase.ParseRecurringCharges
import app.nemla.core.domain.usecase.RevokeAllAiConsents
import app.nemla.core.domain.usecase.SaveDebitSettings
import app.nemla.core.domain.usecase.SaveGlobalBudget
import app.nemla.core.domain.usecase.SaveProfileIdentity
import app.nemla.core.domain.usecase.SaveRecurringCharges
import app.nemla.core.domain.usecase.SignOut
import app.nemla.core.domain.usecase.SuggestBudget
import app.nemla.core.domain.usecase.UpdateCategory
import app.nemla.core.domain.usecase.UpdateCurrency
import app.nemla.core.domain.usecase.UpdatePreferences
import app.nemla.feature.settings.budget.BudgetViewModel
import app.nemla.feature.settings.card.CardClock
import app.nemla.feature.settings.card.CardViewModel
import app.nemla.feature.settings.categories.CategoriesViewModel
import app.nemla.feature.settings.credits.CreditsViewModel
import app.nemla.feature.settings.preferences.PreferencesViewModel
import app.nemla.feature.settings.profile.ProfileViewModel
import app.nemla.feature.settings.recurring.RecurringViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import kotlin.time.Clock

val featureSettingsModule: Module =
    module {
        factoryOf(::GetUserProfile)
        factoryOf(::DeleteAccount)
        factoryOf(::SignOut)
        factoryOf(::ListRecurringCharges)
        factoryOf(::SuggestBudget)
        factoryOf(::SaveGlobalBudget)
        factoryOf(::ListCategories)
        factoryOf(::ParseRecurringCharges)
        factoryOf(::SaveRecurringCharges)
        factoryOf(::DeleteRecurringCharge)
        factoryOf(::SaveProfileIdentity)
        factoryOf(::UpdateCurrency)
        factoryOf(::ObservePreferences)
        factoryOf(::UpdatePreferences)
        factoryOf(::CurrentLanguages)
        factoryOf(::ObserveAiConsents)
        factoryOf(::RevokeAllAiConsents)
        factoryOf(::GetDebitSettings)
        factoryOf(::SaveDebitSettings)
        factoryOf(::GetMonthlyQuota)
        factoryOf(::AddCategory)
        factoryOf(::UpdateCategory)
        factoryOf(::DeleteCategory)
        single<CardClock> { CardClock { Clock.System.todayIn(TimeZone.currentSystemDefault()) } }
        viewModel { params -> SettingsViewModel(params.get(), get(), get(), get()) }
        viewModelOf(::BudgetViewModel)
        viewModelOf(::RecurringViewModel)
        viewModelOf(::ProfileViewModel)
        viewModelOf(::PreferencesViewModel)
        viewModelOf(::CardViewModel)
        viewModelOf(::CreditsViewModel)
        viewModelOf(::CategoriesViewModel)
    }
