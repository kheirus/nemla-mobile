package app.nemla.feature.reports

import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.ReportSheet
import app.nemla.core.domain.model.ReportYearGroup
import app.nemla.core.domain.model.ReviewPeriod

internal sealed interface ReportContent {
    data object Loading : ReportContent

    data object Failed : ReportContent

    data class Loaded(
        val sheet: ReportSheet,
    ) : ReportContent
}

internal data class ReportDialogUiState(
    val period: ReviewPeriod,
    val language: AppLanguage,
    val content: ReportContent = ReportContent.Loading,
) {
    override fun toString(): String = "ReportDialogUiState(period=$period, content=${content::class.simpleName})"
}

internal sealed interface ReportDialogAction {
    data object RetryClicked : ReportDialogAction
}

internal data class ReportNoticeUiState(
    val language: AppLanguage,
    val periods: List<ReviewPeriod> = emptyList(),
    val opened: ReviewPeriod? = null,
)

internal sealed interface ReportNoticeAction {
    data object Started : ReportNoticeAction

    data class NoticeClicked(
        val period: ReviewPeriod,
    ) : ReportNoticeAction

    data object DialogClosed : ReportNoticeAction
}

internal sealed interface HistoryContent {
    data object Loading : HistoryContent

    data object Failed : HistoryContent

    data class Loaded(
        val groups: List<ReportYearGroup>,
    ) : HistoryContent
}

internal data class ReportHistoryUiState(
    val language: AppLanguage,
    val content: HistoryContent = HistoryContent.Loading,
    val opened: ReviewPeriod? = null,
) {
    override fun toString(): String = "ReportHistoryUiState(content=${content::class.simpleName}, opened=$opened)"
}

internal sealed interface ReportHistoryAction {
    data object Started : ReportHistoryAction

    data object RetryClicked : ReportHistoryAction

    data class PeriodClicked(
        val period: ReviewPeriod,
    ) : ReportHistoryAction

    data object DialogClosed : ReportHistoryAction
}
