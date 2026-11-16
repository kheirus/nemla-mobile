package app.nemla.feature.settings.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AmountInput
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.BudgetRules
import app.nemla.core.domain.model.Category
import app.nemla.core.domain.model.CategoryBudget
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.CategoryNames
import app.nemla.core.domain.model.CategoryUpdate
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.usecase.AddCategory
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.DeleteCategory
import app.nemla.core.domain.usecase.GetUserProfile
import app.nemla.core.domain.usecase.ListCategories
import app.nemla.core.domain.usecase.SuggestBudget
import app.nemla.core.domain.usecase.UpdateCategory
import app.nemla.feature.settings.common.Notice
import app.nemla.feature.settings.common.decimalSeparator
import app.nemla.feature.settings.common.error
import app.nemla.feature.settings.common.success
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class CategoriesMessage { ADDED, UPDATED, DELETED, FAILED }

internal data class CategoryRow(
    val category: Category,
    val budgetText: String,
    val savedBudgetText: String,
    val period: BudgetPeriod,
    val estimateCents: Long?,
) {
    val id: CategoryId get() = category.id

    val budgetable: Boolean get() = CategoryNames.isBudgetable(category)

    val suggestions: List<String> get() = CategoryNames.suggestions(category.name)
}

internal data class CategoriesUiState(
    val currency: Currency = Currency.Default,
    val rows: List<CategoryRow> = emptyList(),
    val newName: String = "",
    val isAdding: Boolean = false,
    val notice: Notice<CategoriesMessage>? = null,
) {
    override fun toString(): String = "CategoriesUiState(rows=${rows.size}, isAdding=$isAdding)"
}

internal sealed interface CategoriesAction {
    data object Opened : CategoriesAction

    data class NewNameChanged(
        val text: String,
    ) : CategoriesAction

    data object AddClicked : CategoriesAction

    data class VisibilityToggled(
        val id: CategoryId,
        val visible: Boolean,
    ) : CategoriesAction

    data class RenameClicked(
        val id: CategoryId,
        val name: String,
    ) : CategoriesAction

    data class DeleteClicked(
        val id: CategoryId,
    ) : CategoriesAction

    data class BudgetChanged(
        val id: CategoryId,
        val text: String,
    ) : CategoriesAction

    data class BudgetCommitted(
        val id: CategoryId,
    ) : CategoriesAction

    data class PeriodSelected(
        val id: CategoryId,
        val period: BudgetPeriod,
    ) : CategoriesAction

    data class EstimateClicked(
        val id: CategoryId,
    ) : CategoriesAction

    data object NoticeDismissed : CategoriesAction
}

internal class CategoriesViewModel(
    private val getProfile: GetUserProfile,
    private val listCategories: ListCategories,
    private val suggestBudget: SuggestBudget,
    private val addCategory: AddCategory,
    private val updateCategory: UpdateCategory,
    private val deleteCategory: DeleteCategory,
    private val languages: CurrentLanguages,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CategoriesUiState())
    val state: StateFlow<CategoriesUiState> = mutableState.asStateFlow()

    private var estimates: Map<CategoryId, Long> = emptyMap()

    fun onAction(action: CategoriesAction) {
        when (action) {
            CategoriesAction.Opened -> {
                load()
            }

            is CategoriesAction.NewNameChanged -> {
                mutableState.update { it.copy(newName = action.text) }
            }

            CategoriesAction.AddClicked -> {
                add()
            }

            is CategoriesAction.VisibilityToggled -> {
                toggleVisibility(action.id, action.visible)
            }

            is CategoriesAction.RenameClicked -> {
                rename(action.id, action.name)
            }

            is CategoriesAction.DeleteClicked -> {
                delete(action.id)
            }

            is CategoriesAction.BudgetChanged -> {
                if (AmountInput.isEditable(
                        action.text,
                    )
                ) {
                    updateRow(action.id) { it.copy(budgetText = action.text) }
                }
            }

            is CategoriesAction.BudgetCommitted -> {
                commitBudget(action.id)
            }

            is CategoriesAction.PeriodSelected -> {
                selectPeriod(action.id, action.period)
            }

            is CategoriesAction.EstimateClicked -> {
                applyEstimate(action.id)
            }

            CategoriesAction.NoticeDismissed -> {
                mutableState.update { it.copy(notice = null) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val profile = getProfile()
            if (profile is AppResult.Success) mutableState.update { it.copy(currency = profile.value.currency) }
            reload()
            val estimate = suggestBudget()
            if (estimate is AppResult.Success) {
                estimates = estimate.value.perCategory.associate { it.categoryId to it.monthlyCents }
                mutableState.update { state -> state.copy(rows = state.rows.map { it.copy(estimateCents = estimates[it.id]) }) }
            }
        }
    }

    private suspend fun reload() {
        val result = listCategories()
        if (result is AppResult.Success) {
            val previous = mutableState.value.rows.associateBy { it.id }
            mutableState.update { state -> state.copy(rows = result.value.map { toRow(it, previous[it.id]) }) }
        }
    }

    private fun toRow(
        category: Category,
        previous: CategoryRow?,
    ): CategoryRow {
        val (cents, period) =
            when (val budget = category.budget) {
                CategoryBudget.None -> null to (previous?.period ?: BudgetPeriod.MONTH)
                is CategoryBudget.Monthly -> budget.cents to BudgetPeriod.MONTH
                is CategoryBudget.Annual -> budget.cents to BudgetPeriod.YEAR
            }
        val text = cents?.let(::format).orEmpty()
        return CategoryRow(category, text, text, period, estimates[category.id])
    }

    private fun format(cents: Long): String {
        val separator = decimalSeparator(languages.ui())
        val text = AmountInput.format(cents, separator)
        return if (cents % 100 == 0L) text.substringBefore(separator) else text
    }

    private fun updateRow(
        id: CategoryId,
        transform: (CategoryRow) -> CategoryRow,
    ) {
        mutableState.update { state -> state.copy(rows = state.rows.map { if (it.id == id) transform(it) else it }) }
    }

    private fun row(id: CategoryId): CategoryRow? = mutableState.value.rows.firstOrNull { it.id == id }

    private fun add() {
        val current = mutableState.value
        if (current.isAdding || current.newName.isBlank()) return
        mutableState.update { it.copy(isAdding = true, notice = null) }
        viewModelScope.launch {
            val result = addCategory(current.newName, current.rows.map { it.category })
            if (result is AppResult.Success) {
                mutableState.update { it.copy(isAdding = false, newName = "", notice = success(CategoriesMessage.ADDED)) }
                reload()
            } else {
                mutableState.update { it.copy(isAdding = false, notice = error(CategoriesMessage.FAILED)) }
            }
        }
    }

    private fun toggleVisibility(
        id: CategoryId,
        visible: Boolean,
    ) {
        val before = row(id) ?: return
        updateRow(id) { it.copy(category = it.category.copy(visible = visible)) }
        viewModelScope.launch {
            if (updateCategory(id, CategoryUpdate(visible = visible)) is AppResult.Failure) {
                updateRow(id) { it.copy(category = it.category.copy(visible = before.category.visible)) }
                mutableState.update { it.copy(notice = error(CategoriesMessage.FAILED)) }
            }
        }
    }

    private fun rename(
        id: CategoryId,
        name: String,
    ) {
        viewModelScope.launch {
            if (updateCategory(id, CategoryUpdate(name = name)) is AppResult.Success) {
                updateRow(id) { it.copy(category = it.category.copy(name = name)) }
                mutableState.update { it.copy(notice = success(CategoriesMessage.UPDATED)) }
            } else {
                mutableState.update { it.copy(notice = error(CategoriesMessage.FAILED)) }
            }
        }
    }

    private fun delete(id: CategoryId) {
        val target = row(id)?.category ?: return
        viewModelScope.launch {
            if (deleteCategory(target) is AppResult.Success) {
                mutableState.update { state ->
                    state.copy(rows = state.rows.filterNot { it.id == id }, notice = success(CategoriesMessage.DELETED))
                }
            } else {
                mutableState.update { it.copy(notice = error(CategoriesMessage.FAILED)) }
            }
        }
    }

    private fun commitBudget(id: CategoryId) {
        val current = row(id) ?: return
        if (current.budgetText == current.savedBudgetText) return
        save(current, current.period)
    }

    private fun selectPeriod(
        id: CategoryId,
        period: BudgetPeriod,
    ) {
        val current = row(id) ?: return
        updateRow(id) { it.copy(period = period) }
        if (current.budgetText.isNotBlank() &&
            AmountInput.parseCents(current.budgetText) != null
        ) {
            save(current.copy(period = period), period)
        }
    }

    private fun applyEstimate(id: CategoryId) {
        val current = row(id) ?: return
        val estimate = current.estimateCents ?: return
        val text = format(BudgetRules.categoryEstimate(estimate, current.period))
        val next = current.copy(budgetText = text)
        updateRow(id) { next }
        save(next, current.period)
    }

    private fun save(
        current: CategoryRow,
        period: BudgetPeriod,
    ) {
        val cents =
            current.budgetText
                .takeIf { it.isNotBlank() }
                ?.let(AmountInput::parseCents)
                ?.takeIf { it >= 0 }
        val budget = BudgetRules.categoryBudget(cents, period)
        viewModelScope.launch {
            if (updateCategory(current.id, CategoryUpdate(budget = budget)) is AppResult.Success) {
                updateRow(current.id) { it.copy(savedBudgetText = current.budgetText, category = it.category.copy(budget = budget)) }
            } else {
                mutableState.update { it.copy(notice = error(CategoriesMessage.FAILED)) }
            }
        }
    }
}
