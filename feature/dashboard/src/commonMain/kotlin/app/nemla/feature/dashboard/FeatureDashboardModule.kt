package app.nemla.feature.dashboard

import app.nemla.core.domain.usecase.BootstrapAndLoadProfile
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.DeleteExpense
import app.nemla.core.domain.usecase.GetDeferredDebit
import app.nemla.core.domain.usecase.ListCategories
import app.nemla.core.domain.usecase.ListExpenses
import app.nemla.core.domain.usecase.ListRecurringCharges
import app.nemla.core.domain.usecase.MaterializeRecurringCharges
import app.nemla.core.domain.usecase.SearchExpensesByDate
import app.nemla.core.domain.usecase.SignOut
import app.nemla.core.domain.usecase.UpdateExpense
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val featureDashboardModule: Module =
    module {
        factoryOf(::SignOut)
        factoryOf(::ListCategories)
        factoryOf(::BootstrapAndLoadProfile)
        factoryOf(::ListExpenses)
        factoryOf(::SearchExpensesByDate)
        factoryOf(::ListRecurringCharges)
        factoryOf(::MaterializeRecurringCharges)
        factoryOf(::GetDeferredDebit)
        factoryOf(::UpdateExpense)
        factoryOf(::DeleteExpense)
        single<DashboardClock> { SystemDashboardClock }
        factoryOf(::CurrentLanguages)
        single<UiLanguage> { PreferredUiLanguage(get()) }
        viewModelOf(::DashboardViewModel)
    }
