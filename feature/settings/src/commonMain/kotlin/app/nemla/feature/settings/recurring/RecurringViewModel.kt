package app.nemla.feature.settings.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AmountInput
import app.nemla.core.domain.model.BudgetRules
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.ChargeFrequency
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.RecurringCharge
import app.nemla.core.domain.model.RecurringChargeDraft
import app.nemla.core.domain.model.RecurringChargeId
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.DeleteRecurringCharge
import app.nemla.core.domain.usecase.GetUserProfile
import app.nemla.core.domain.usecase.ListCategories
import app.nemla.core.domain.usecase.ListRecurringCharges
import app.nemla.core.domain.usecase.ParseRecurringCharges
import app.nemla.core.domain.usecase.SaveRecurringCharges
import app.nemla.feature.settings.common.Notice
import app.nemla.feature.settings.common.decimalSeparator
import app.nemla.feature.settings.common.error
import app.nemla.feature.settings.common.info
import app.nemla.feature.settings.common.success
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface RecurringMessage {
    data object NothingDetected : RecurringMessage

    data object AnalysisFailed : RecurringMessage

    data object Saved : RecurringMessage

    data object SaveFailed : RecurringMessage

    data object Deleted : RecurringMessage

    data object DeleteFailed : RecurringMessage

    class Dictation(
        val text: String,
    ) : RecurringMessage
}

internal data class CategoryOption(
    val id: CategoryId,
    val name: String,
    val colorHex: String,
)

internal data class DraftRow(
    val key: Int,
    val name: String,
    val amountText: String,
    val categoryId: CategoryId?,
    val frequency: ChargeFrequency,
    val dayText: String,
) {
    val amountCents: Long get() = AmountInput.parseCents(amountText) ?: 0

    val day: Int get() = (dayText.toIntOrNull() ?: 1).coerceIn(SaveRecurringCharges.DAY_MIN, SaveRecurringCharges.DAY_MAX)

    val isComplete: Boolean get() = name.isNotBlank() && amountCents > 0

    override fun toString(): String = "DraftRow(key=$key, frequency=$frequency)"
}

internal data class RecurringUiState(
    val currency: Currency = Currency.Default,
    val categories: List<CategoryOption> = emptyList(),
    val text: String = "",
    val isAnalyzing: Boolean = false,
    val isDictating: Boolean = false,
    val isManualOpen: Boolean = false,
    val drafts: List<DraftRow> = emptyList(),
    val charges: List<RecurringCharge> = emptyList(),
    val page: Int = 0,
    val isSaving: Boolean = false,
    val deleting: Set<RecurringChargeId> = emptySet(),
    val notice: Notice<RecurringMessage>? = null,
) {
    val canAnalyze: Boolean get() = !isAnalyzing && !isDictating && ParseRecurringCharges.canAnalyze(text)

    val canSave: Boolean get() = drafts.isNotEmpty() && drafts.all { it.isComplete } && !isSaving

    val pageCount: Int get() = maxOf(1, (charges.size + PAGE_SIZE - 1) / PAGE_SIZE)

    val visibleCharges: List<RecurringCharge> get() = charges.drop(page * PAGE_SIZE).take(PAGE_SIZE)

    val monthlyTotalCents: Long get() = BudgetRules.fixedMonthlyCents(charges)

    val yearlyTotalCents: Long get() = BudgetRules.fixedYearlyCents(charges)

    override fun toString(): String = "RecurringUiState(drafts=${drafts.size}, charges=${charges.size}, page=$page)"

    companion object {
        const val PAGE_SIZE: Int = 6
    }
}

internal sealed interface RecurringAction {
    data object Opened : RecurringAction

    data class TextChanged(
        val text: String,
    ) : RecurringAction {
        override fun toString(): String = "TextChanged(***)"
    }

    data object AnalyzeClicked : RecurringAction

    data class DictationActiveChanged(
        val active: Boolean,
    ) : RecurringAction

    class Transcribed(
        val text: String,
    ) : RecurringAction {
        override fun toString(): String = "Transcribed(***)"
    }

    class DictationMessage(
        val text: String,
    ) : RecurringAction

    data object ManualToggled : RecurringAction

    data object BlankRowAdded : RecurringAction

    data class RowNameChanged(
        val key: Int,
        val name: String,
    ) : RecurringAction {
        override fun toString(): String = "RowNameChanged(key=$key)"
    }

    data class RowAmountChanged(
        val key: Int,
        val text: String,
    ) : RecurringAction {
        override fun toString(): String = "RowAmountChanged(key=$key)"
    }

    data class RowCategorySelected(
        val key: Int,
        val id: CategoryId,
    ) : RecurringAction

    data class RowFrequencySelected(
        val key: Int,
        val frequency: ChargeFrequency,
    ) : RecurringAction

    data class RowDayChanged(
        val key: Int,
        val text: String,
    ) : RecurringAction

    data class RowRemoved(
        val key: Int,
    ) : RecurringAction

    data object SaveClicked : RecurringAction

    data class DeleteClicked(
        val id: RecurringChargeId,
    ) : RecurringAction

    data object PreviousPage : RecurringAction

    data object NextPage : RecurringAction

    data object NoticeDismissed : RecurringAction
}

internal class RecurringViewModel(
    private val getProfile: GetUserProfile,
    private val listCategories: ListCategories,
    private val listRecurring: ListRecurringCharges,
    private val parseCharges: ParseRecurringCharges,
    private val saveCharges: SaveRecurringCharges,
    private val deleteCharge: DeleteRecurringCharge,
    private val languages: CurrentLanguages,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RecurringUiState())
    val state: StateFlow<RecurringUiState> = mutableState.asStateFlow()

    private var nextKey = 0

    fun onAction(action: RecurringAction) {
        when (action) {
            RecurringAction.Opened -> {
                load()
            }

            is RecurringAction.TextChanged -> {
                mutableState.update { it.copy(text = action.text) }
            }

            RecurringAction.AnalyzeClicked -> {
                if (mutableState.value.canAnalyze) analyze(mutableState.value.text)
            }

            is RecurringAction.DictationActiveChanged -> {
                mutableState.update { it.copy(isDictating = action.active) }
            }

            is RecurringAction.Transcribed -> {
                onTranscribed(action.text)
            }

            is RecurringAction.DictationMessage -> {
                mutableState.update { it.copy(notice = error(RecurringMessage.Dictation(action.text))) }
            }

            RecurringAction.ManualToggled -> {
                mutableState.update { it.copy(isManualOpen = !it.isManualOpen) }
            }

            RecurringAction.BlankRowAdded -> {
                addBlankRow()
            }

            is RecurringAction.RowNameChanged -> {
                updateRow(action.key) { it.copy(name = action.name) }
            }

            is RecurringAction.RowAmountChanged -> {
                if (AmountInput.isEditable(action.text)) updateRow(action.key) { it.copy(amountText = action.text) }
            }

            is RecurringAction.RowCategorySelected -> {
                updateRow(action.key) { it.copy(categoryId = action.id) }
            }

            is RecurringAction.RowFrequencySelected -> {
                updateRow(action.key) { it.copy(frequency = action.frequency) }
            }

            is RecurringAction.RowDayChanged -> {
                changeDay(action.key, action.text)
            }

            is RecurringAction.RowRemoved -> {
                mutableState.update { state ->
                    state.copy(
                        drafts =
                            state.drafts.filterNot {
                                it.key ==
                                    action.key
                            },
                    )
                }
            }

            RecurringAction.SaveClicked -> {
                save()
            }

            is RecurringAction.DeleteClicked -> {
                delete(action.id)
            }

            RecurringAction.PreviousPage -> {
                mutableState.update { it.copy(page = (it.page - 1).coerceAtLeast(0)) }
            }

            RecurringAction.NextPage -> {
                mutableState.update { it.copy(page = (it.page + 1).coerceAtMost(it.pageCount - 1)) }
            }

            RecurringAction.NoticeDismissed -> {
                mutableState.update { it.copy(notice = null) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val profile = getProfile()
            if (profile is AppResult.Success) mutableState.update { it.copy(currency = profile.value.currency) }
            val categories = listCategories()
            if (categories is AppResult.Success) {
                mutableState.update { state ->
                    state.copy(categories = categories.value.map { CategoryOption(it.id, it.name, it.colorHex) })
                }
            }
            reloadCharges()
        }
    }

    private suspend fun reloadCharges() {
        val charges = listRecurring()
        if (charges is AppResult.Success) {
            mutableState.update {
                it.copy(
                    charges = charges.value,
                    page =
                        it.page.coerceAtMost(
                            maxOf(
                                0,
                                (charges.value.size - 1) / RecurringUiState.PAGE_SIZE,
                            ),
                        ),
                )
            }
        }
    }

    private fun onTranscribed(transcript: String) {
        val cleaned = transcript.trim()
        if (cleaned.isEmpty()) return
        mutableState.update { state ->
            val current = state.text.trim()
            state.copy(text = if (current.isEmpty()) cleaned else "$current, $cleaned")
        }
        if (ParseRecurringCharges.canAnalyze(cleaned)) analyze(cleaned)
    }

    private fun analyze(text: String) {
        mutableState.update { it.copy(isAnalyzing = true, notice = null) }
        viewModelScope.launch {
            when (val result = parseCharges(text, languages.dictation())) {
                is AppResult.Success -> {
                    val rows = result.value.map(::toRow)
                    mutableState.update {
                        it.copy(
                            isAnalyzing = false,
                            drafts = it.drafts + rows,
                            notice = if (rows.isEmpty()) info(RecurringMessage.NothingDetected) else null,
                        )
                    }
                }

                is AppResult.Failure -> {
                    mutableState.update { it.copy(isAnalyzing = false, notice = error(RecurringMessage.AnalysisFailed)) }
                }
            }
        }
    }

    private fun toRow(draft: RecurringChargeDraft): DraftRow =
        DraftRow(
            key = nextKey++,
            name = draft.name,
            amountText = AmountInput.format(draft.amountCents, decimalSeparator(languages.ui())),
            categoryId = draft.categoryId?.takeIf { id -> mutableState.value.categories.any { it.id == id } },
            frequency = draft.frequency,
            dayText = draft.dayOfMonth.toString(),
        )

    private fun addBlankRow() {
        mutableState.update { state ->
            state.copy(
                drafts =
                    state.drafts +
                        DraftRow(nextKey++, "", "0", state.categories.firstOrNull()?.id, ChargeFrequency.MONTHLY, "1"),
            )
        }
    }

    private fun changeDay(
        key: Int,
        text: String,
    ) {
        if (text.length > 2 || text.any { !it.isDigit() }) return
        updateRow(key) { it.copy(dayText = text) }
    }

    private fun updateRow(
        key: Int,
        transform: (DraftRow) -> DraftRow,
    ) {
        mutableState.update { state -> state.copy(drafts = state.drafts.map { if (it.key == key) transform(it) else it }) }
    }

    private fun save() {
        val current = mutableState.value
        if (!current.canSave) return
        val drafts = current.drafts.map { RecurringChargeDraft(it.name, it.amountCents, it.frequency, it.day, it.categoryId) }
        mutableState.update { it.copy(isSaving = true, notice = null) }
        viewModelScope.launch {
            when (saveCharges(drafts)) {
                is AppResult.Success -> {
                    mutableState.update {
                        it.copy(
                            isSaving = false,
                            drafts = emptyList(),
                            text = "",
                            notice = success(RecurringMessage.Saved),
                        )
                    }
                    reloadCharges()
                }

                is AppResult.Failure -> {
                    mutableState.update { it.copy(isSaving = false, notice = error(RecurringMessage.SaveFailed)) }
                }
            }
        }
    }

    private fun delete(id: RecurringChargeId) {
        if (id in mutableState.value.deleting) return
        mutableState.update { it.copy(deleting = it.deleting + id, notice = null) }
        viewModelScope.launch {
            val result = deleteCharge(id)
            mutableState.update {
                it.copy(
                    deleting = it.deleting - id,
                    notice = if (result is AppResult.Success) success(RecurringMessage.Deleted) else error(RecurringMessage.DeleteFailed),
                )
            }
            if (result is AppResult.Success) reloadCharges()
        }
    }
}
