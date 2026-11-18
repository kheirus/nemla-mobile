package app.nemla.feature.reports

import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.InboxItem
import app.nemla.core.domain.model.InboxItemId
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

internal sealed interface ReportRequest {
    val period: ReviewPeriod?

    data class Compute(
        override val period: ReviewPeriod,
    ) : ReportRequest

    class Delivered(
        period: ReviewPeriod?,
        val sheet: ReportSheet?,
    ) : ReportRequest {
        override val period: ReviewPeriod? = period ?: sheet?.review?.period

        override fun equals(other: Any?): Boolean = other is Delivered && other.period == period && other.sheet == sheet

        override fun hashCode(): Int = 31 * period.hashCode() + sheet.hashCode()

        override fun toString(): String = "Delivered(period=$period, sheet=${sheet != null})"
    }
}

internal data class ReportDialogUiState(
    val period: ReviewPeriod?,
    val language: AppLanguage,
    val content: ReportContent = ReportContent.Loading,
    val canRetry: Boolean = period != null,
) {
    override fun toString(): String = "ReportDialogUiState(period=$period, content=${content::class.simpleName}, canRetry=$canRetry)"
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

internal class OpenedInboxReport(
    val id: InboxItemId,
    val request: ReportRequest.Delivered,
) {
    override fun equals(other: Any?): Boolean = other is OpenedInboxReport && other.id == id && other.request == request

    override fun hashCode(): Int = 31 * id.hashCode() + request.hashCode()

    override fun toString(): String = "OpenedInboxReport(id=${id.value})"
}

internal data class InboxBannersUiState(
    val language: AppLanguage,
    val items: List<InboxItem> = emptyList(),
    val opened: OpenedInboxReport? = null,
) {
    override fun toString(): String = "InboxBannersUiState(items=${items.size}, opened=$opened)"
}

internal sealed interface InboxBannersAction {
    data object Started : InboxBannersAction

    data class ItemClicked(
        val id: InboxItemId,
    ) : InboxBannersAction

    data class DismissClicked(
        val id: InboxItemId,
    ) : InboxBannersAction

    data object DialogClosed : InboxBannersAction
}
