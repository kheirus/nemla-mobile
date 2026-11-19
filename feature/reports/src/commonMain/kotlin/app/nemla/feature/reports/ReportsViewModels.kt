package app.nemla.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.InboxItemId
import app.nemla.core.domain.model.InboxKind
import app.nemla.core.domain.model.ReviewPeriod
import app.nemla.core.domain.usecase.DismissInboxItem
import app.nemla.core.domain.usecase.FindArrivedReports
import app.nemla.core.domain.usecase.ListInAppNotices
import app.nemla.core.domain.usecase.ListReportHistory
import app.nemla.core.domain.usecase.LoadPeriodReport
import app.nemla.core.domain.usecase.MarkReportViewed
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ReportDialogViewModel(
    request: ReportRequest,
    private val loadReport: LoadPeriodReport,
    private val language: ReportsLanguage,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            ReportDialogUiState(
                period = request.period,
                language = language.current(),
                content =
                    when (request) {
                        is ReportRequest.Compute -> ReportContent.Loading
                        is ReportRequest.Delivered -> request.sheet?.let(ReportContent::Loaded) ?: ReportContent.Failed
                    },
            ),
        )
    val state: StateFlow<ReportDialogUiState> = mutableState.asStateFlow()

    private var job: Job? = null

    init {
        if (request is ReportRequest.Compute) load()
    }

    fun onAction(action: ReportDialogAction) {
        when (action) {
            ReportDialogAction.RetryClicked -> if (mutableState.value.content == ReportContent.Failed) load()
        }
    }

    private fun load() {
        val period = mutableState.value.period ?: return
        if (job?.isActive == true) return
        mutableState.update { it.copy(language = language.current(), content = ReportContent.Loading) }
        job =
            viewModelScope.launch {
                val content =
                    when (val result = loadReport(period)) {
                        is AppResult.Success -> ReportContent.Loaded(result.value)
                        is AppResult.Failure -> ReportContent.Failed
                    }
                mutableState.update { it.copy(content = content) }
            }
    }
}

internal class ReportNoticeViewModel(
    private val findArrived: FindArrivedReports,
    private val markViewed: MarkReportViewed,
    private val clock: ReportsClock,
    private val language: ReportsLanguage,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReportNoticeUiState(language = language.current()))
    val state: StateFlow<ReportNoticeUiState> = mutableState.asStateFlow()

    private val seen = mutableSetOf<ReviewPeriod>()
    private var job: Job? = null

    fun onAction(action: ReportNoticeAction) {
        when (action) {
            ReportNoticeAction.Started -> refresh()
            is ReportNoticeAction.NoticeClicked -> open(action.period)
            ReportNoticeAction.DialogClosed -> mutableState.update { it.copy(opened = null) }
        }
    }

    private fun refresh() {
        if (job?.isActive == true) return
        mutableState.update { it.copy(language = language.current()) }
        job =
            viewModelScope.launch {
                val periods =
                    when (val result = findArrived(clock.today())) {
                        is AppResult.Success -> result.value.filterNot { it in seen }
                        is AppResult.Failure -> emptyList()
                    }
                mutableState.update { it.copy(periods = periods) }
            }
    }

    private fun open(period: ReviewPeriod) {
        if (period !in mutableState.value.periods) return
        seen += period
        mutableState.update { state -> state.copy(periods = state.periods - period, opened = period) }
        viewModelScope.launch { markViewed(period) }
    }
}

internal class ReportHistoryViewModel(
    private val listHistory: ListReportHistory,
    private val clock: ReportsClock,
    private val language: ReportsLanguage,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReportHistoryUiState(language = language.current()))
    val state: StateFlow<ReportHistoryUiState> = mutableState.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun onAction(action: ReportHistoryAction) {
        when (action) {
            ReportHistoryAction.Started -> mutableState.update { it.copy(language = language.current()) }
            ReportHistoryAction.RetryClicked -> if (mutableState.value.content == HistoryContent.Failed) load()
            is ReportHistoryAction.PeriodClicked -> mutableState.update { it.copy(opened = action.period) }
            ReportHistoryAction.DialogClosed -> mutableState.update { it.copy(opened = null) }
        }
    }

    private fun load() {
        if (job?.isActive == true) return
        mutableState.update { it.copy(content = HistoryContent.Loading) }
        job =
            viewModelScope.launch {
                val content =
                    when (val result = listHistory(clock.today())) {
                        is AppResult.Success -> HistoryContent.Loaded(result.value)
                        is AppResult.Failure -> HistoryContent.Failed
                    }
                mutableState.update { it.copy(content = content) }
            }
    }
}

internal class InboxBannersViewModel(
    private val listNotices: ListInAppNotices,
    private val dismissItem: DismissInboxItem,
    private val language: ReportsLanguage,
) : ViewModel() {
    private val mutableState = MutableStateFlow(InboxBannersUiState(language = language.current()))
    val state: StateFlow<InboxBannersUiState> = mutableState.asStateFlow()

    private val hidden = mutableSetOf<InboxItemId>()
    private var job: Job? = null

    fun onAction(action: InboxBannersAction) {
        when (action) {
            InboxBannersAction.Started -> refresh()
            is InboxBannersAction.ItemClicked -> open(action.id)
            is InboxBannersAction.DismissClicked -> dismiss(action.id)
            InboxBannersAction.DialogClosed -> mutableState.update { it.copy(opened = null) }
        }
    }

    private fun refresh() {
        if (job?.isActive == true) return
        mutableState.update { it.copy(language = language.current()) }
        job =
            viewModelScope.launch {
                when (val result = listNotices()) {
                    is AppResult.Success -> mutableState.update { state -> state.copy(items = result.value.filterNot { it.id in hidden }) }
                    is AppResult.Failure -> Unit
                }
            }
    }

    private fun open(id: InboxItemId) {
        val item = mutableState.value.items.firstOrNull { it.id == id } ?: return
        if (item.kind != InboxKind.DAILY_REMINDER) {
            mutableState.update { it.copy(opened = OpenedInboxReport(id, ReportRequest.Delivered(item.period, item.report))) }
        }
        dismiss(id)
    }

    private fun dismiss(id: InboxItemId) {
        if (mutableState.value.items.none { it.id == id }) return
        hidden += id
        mutableState.update { state -> state.copy(items = state.items.filterNot { it.id == id }) }
        viewModelScope.launch { dismissItem(id) }
    }
}
