package app.nemla.feature.statementimport

import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.GetMonthlyQuota
import app.nemla.core.domain.usecase.GrantAiConsent
import app.nemla.core.domain.usecase.ImportStatement
import app.nemla.core.domain.usecase.ObserveAiConsents
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val featureImportModule: Module =
    module {
        factoryOf(::ImportStatement)
        factoryOf(::GetMonthlyQuota)
        factoryOf(::ObserveAiConsents)
        factoryOf(::GrantAiConsent)
        factoryOf(::CurrentLanguages)
        viewModelOf(::StatementImportViewModel)
    }
