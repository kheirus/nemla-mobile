package app.nemla.feature.dashboard

import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ExpenseSource
import app.nemla.core.domain.model.PaymentMethod
import app.nemla.core.domain.model.PendingReview
import app.nemla.core.domain.model.PeriodCursor
import app.nemla.core.domain.model.PeriodKind
import app.nemla.feature.dashboard.logic.DashboardView
import app.nemla.feature.dashboard.logic.ExpenseRow
import kotlinx.datetime.LocalDate

internal enum class DashboardError { OFFLINE, TIMEOUT, GENERIC }

internal enum class DashboardNotice { EXPENSE_UPDATED, EXPENSE_DELETED }

internal enum class SpendingTab { BREAKDOWN, BUDGETS }

internal enum class EditorError { SAVE_FAILED, DELETE_FAILED, OFFLINE, TIMEOUT }

internal data class CategoryChoice(
    val id: CategoryId,
    val name: String,
    val colorHex: String,
)

internal data class SearchUiState(
    val date: LocalDate? = null,
    val isLoading: Boolean = false,
    val results: List<ExpenseRow>? = null,
    val failed: Boolean = false,
) {
    val isActive: Boolean get() = date != null

    val totalCents: Long get() = results.orEmpty().sumOf { it.amountCents }

    override fun toString(): String = "SearchUiState(active=$isActive, results=${results?.size}, failed=$failed)"
}

internal data class ExpenseEditorState(
    val id: ExpenseId,
    val label: String?,
    val source: ExpenseSource,
    val amountText: String,
    val date: LocalDate,
    val categoryId: CategoryId?,
    val paymentMethod: PaymentMethod,
    val debitDate: LocalDate?,
    val amountInvalid: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val confirmingDelete: Boolean = false,
    val error: EditorError? = null,
) {
    val isBusy: Boolean get() = isSaving || isDeleting

    override fun toString(): String = "ExpenseEditorState(id=${id.value}, isSaving=$isSaving, isDeleting=$isDeleting, error=$error)"
}

internal data class DashboardUiState(
    val language: AppLanguage,
    val today: LocalDate,
    val cursor: PeriodCursor,
    val view: DashboardView,
    val currency: Currency = Currency.Default,
    val pseudo: String? = null,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isPeriodLoading: Boolean = false,
    val loadError: DashboardError? = null,
    val categories: List<CategoryChoice> = emptyList(),
    val search: SearchUiState = SearchUiState(),
    val spendingTab: SpendingTab = SpendingTab.BREAKDOWN,
    val expandedGroup: String? = null,
    val editor: ExpenseEditorState? = null,
    val notice: DashboardNotice? = null,
    val isSigningOut: Boolean = false,
    val signOutFailed: Boolean = false,
) {
    val effectiveSpendingTab: SpendingTab get() = if (view.spending.hasBudgets) spendingTab else SpendingTab.BREAKDOWN

    override fun toString(): String =
        "DashboardUiState(cursor=$cursor, isInitialLoading=$isInitialLoading, loadError=$loadError, editor=$editor)"
}

internal sealed interface DashboardAction {
    data object Started : DashboardAction

    data object PullToRefresh : DashboardAction

    data object RetryClicked : DashboardAction

    data class PeriodKindSelected(
        val kind: PeriodKind,
    ) : DashboardAction

    data object PreviousPeriodClicked : DashboardAction

    data object NextPeriodClicked : DashboardAction

    data class ChartMonthTapped(
        val monthNumber: Int,
    ) : DashboardAction

    data class FilterToggled(
        val id: CategoryId,
    ) : DashboardAction

    data object FilterAllToggled : DashboardAction

    data class SpendingTabSelected(
        val tab: SpendingTab,
    ) : DashboardAction

    data class GroupToggled(
        val key: String,
    ) : DashboardAction

    data object TodayPreviousPage : DashboardAction

    data object TodayNextPage : DashboardAction

    data class SearchDateSelected(
        val date: LocalDate,
    ) : DashboardAction

    data object SearchCleared : DashboardAction

    data class PendingGroupClicked(
        val index: Int,
    ) : DashboardAction

    data class ExpenseClicked(
        val id: ExpenseId,
    ) : DashboardAction

    data class EditorAmountChanged(
        val text: String,
    ) : DashboardAction {
        override fun toString(): String = "EditorAmountChanged(***)"
    }

    data class EditorDateSelected(
        val date: LocalDate,
    ) : DashboardAction

    data class EditorCategorySelected(
        val id: CategoryId?,
    ) : DashboardAction

    data class EditorPaymentSelected(
        val method: PaymentMethod,
    ) : DashboardAction

    data object EditorSaveClicked : DashboardAction

    data object EditorDeleteClicked : DashboardAction

    data object EditorDeleteConfirmed : DashboardAction

    data object EditorDeleteCancelled : DashboardAction

    data object EditorDismissed : DashboardAction

    data object NoticeDismissed : DashboardAction

    data object SignOutClicked : DashboardAction
}

internal sealed interface DashboardEffect {
    data class OpenReview(
        val review: PendingReview,
    ) : DashboardEffect

    data object SignedOut : DashboardEffect
}
