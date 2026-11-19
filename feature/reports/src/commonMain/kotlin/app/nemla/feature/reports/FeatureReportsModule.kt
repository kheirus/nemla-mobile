package app.nemla.feature.reports

import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.FindArrivedReports
import app.nemla.core.domain.usecase.ListReportHistory
import app.nemla.core.domain.usecase.LoadPeriodReport
import app.nemla.core.domain.usecase.MarkReportViewed
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val featureReportsModule: Module =
    module {
        factoryOf(::LoadPeriodReport)
        factoryOf(::FindArrivedReports)
        factoryOf(::MarkReportViewed)
        factoryOf(::ListReportHistory)
        factoryOf(::CurrentLanguages)
        single<ReportsClock> { SystemReportsClock }
        single<ReportsLanguage> { PreferredReportsLanguage(get()) }
        viewModel { (request: ReportRequest) -> ReportDialogViewModel(request, get(), get()) }
        viewModelOf(::ReportNoticeViewModel)
        viewModelOf(::ReportHistoryViewModel)
        viewModelOf(::InboxBannersViewModel)
    }
