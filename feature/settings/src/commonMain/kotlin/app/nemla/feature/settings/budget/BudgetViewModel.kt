package app.nemla.feature.settings.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AmountInput
import app.nemla.core.domain.model.BudgetEstimate
import app.nemla.core.domain.model.BudgetPeriod
import app.nemla.core.domain.model.BudgetRules
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.GetUserProfile
import app.nemla.core.domain.usecase.ListRecurringCharges
import app.nemla.core.domain.usecase.SaveGlobalBudget
import app.nemla.core.domain.usecase.SuggestBudget
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

internal sealed interface BudgetMessage {
    data object Saved : BudgetMessage

    data object SaveFailed : BudgetMessage

    data class Estimated(
        val monthlyCents: Long,
    ) : BudgetMessage

    data object NoHistory : BudgetMessage

    data object EstimateFailed : BudgetMessage
}

internal data class BudgetUiState(
    val currency: Currency = Currency.Default,
    val amountText: String = "",
    val period: BudgetPeriod = BudgetPeriod.MONTH,
    val estimate: BudgetEstimate? = null,
    val fixedMonthlyCents: Long = 0,
    val isSaving: Boolean = false,
    val isEstimating: Boolean = false,
    val notice: Notice<BudgetMessage>? = null,
) {
    val amountCents: Long? get() = AmountInput.parseCents(amountText)?.takeIf { it > 0 }

    val monthlyCents: Long? get() = amountCents?.let { BudgetRules.monthlyOf(it, period) }

    val remainingCents: Long? get() = monthlyCents?.let { it - fixedMonthlyCents }

    val hasHistory: Boolean get() = estimate?.months?.isNotEmpty() == true

    val canSave: Boolean get() = amountCents != null && !isSaving

    override fun toString(): String = "BudgetUiState(period=$period, isSaving=$isSaving, hasHistory=$hasHistory)"
}

internal sealed interface BudgetAction {
    data object Opened : BudgetAction

    data class AmountChanged(
        val text: String,
    ) : BudgetAction {
        override fun toString(): String = "AmountChanged(***)"
    }

    data class PeriodSelected(
        val period: BudgetPeriod,
    ) : BudgetAction

    data object SaveClicked : BudgetAction

    data object EstimateClicked : BudgetAction

    data object NoticeDismissed : BudgetAction
}

internal class BudgetViewModel(
    private val getProfile: GetUserProfile,
    private val listRecurring: ListRecurringCharges,
    private val suggestBudget: SuggestBudget,
    private val saveBudget: SaveGlobalBudget,
    private val languages: CurrentLanguages,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BudgetUiState())
    val state: StateFlow<BudgetUiState> = mutableState.asStateFlow()

    fun onAction(action: BudgetAction) {
        when (action) {
            BudgetAction.Opened -> {
                load()
            }

            is BudgetAction.AmountChanged -> {
                if (AmountInput.isEditable(
                        action.text,
                    )
                ) {
                    mutableState.update { it.copy(amountText = action.text) }
                }
            }

            is BudgetAction.PeriodSelected -> {
                mutableState.update { it.copy(period = action.period) }
            }

            BudgetAction.SaveClicked -> {
                save()
            }

            BudgetAction.EstimateClicked -> {
                estimate()
            }

            BudgetAction.NoticeDismissed -> {
                mutableState.update { it.copy(notice = null) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val profile = getProfile()
            if (profile is AppResult.Success) {
                val budget = profile.value.budget
                val shown = BudgetRules.displayedAmount(budget)
                mutableState.update {
                    it.copy(
                        currency = profile.value.currency,
                        period = budget.period,
                        amountText = if (shown > 0) format(shown) else "",
                    )
                }
            }
            val charges = listRecurring()
            if (charges is AppResult.Success) {
                mutableState.update { it.copy(fixedMonthlyCents = BudgetRules.fixedMonthlyCents(charges.value)) }
            }
            val estimate = suggestBudget()
            if (estimate is AppResult.Success) mutableState.update { it.copy(estimate = estimate.value) }
        }
    }

    private fun save() {
        val current = mutableState.value
        val cents = current.amountCents ?: return
        if (current.isSaving) return
        mutableState.update { it.copy(isSaving = true, notice = null) }
        viewModelScope.launch {
            val result = saveBudget(cents, current.period)
            mutableState.update {
                it.copy(
                    isSaving = false,
                    notice = if (result is AppResult.Success) success(BudgetMessage.Saved) else error(BudgetMessage.SaveFailed),
                )
            }
        }
    }

    private fun estimate() {
        if (mutableState.value.isEstimating) return
        mutableState.update { it.copy(isEstimating = true, notice = null) }
        viewModelScope.launch {
            when (val result = suggestBudget()) {
                is AppResult.Success -> {
                    val monthly = result.value.monthlyCents
                    mutableState.update {
                        if (monthly <= 0) {
                            it.copy(isEstimating = false, estimate = result.value, notice = info(BudgetMessage.NoHistory))
                        } else {
                            it.copy(
                                isEstimating = false,
                                estimate = result.value,
                                period = BudgetPeriod.MONTH,
                                amountText = format(monthly),
                                notice = success(BudgetMessage.Estimated(monthly)),
                            )
                        }
                    }
                }

                is AppResult.Failure -> {
                    mutableState.update { it.copy(isEstimating = false, notice = error(BudgetMessage.EstimateFailed)) }
                }
            }
        }
    }

    private fun format(cents: Long): String {
        val text = AmountInput.format(cents, decimalSeparator(languages.ui()))
        return if (cents % 100 == 0L) text.substringBefore(decimalSeparator(languages.ui())) else text
    }
}
