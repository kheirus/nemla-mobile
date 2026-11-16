package app.nemla.feature.settings.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.Banks
import app.nemla.core.domain.model.DeferredDebit
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.GetDebitSettings
import app.nemla.core.domain.usecase.SaveDebitSettings
import app.nemla.feature.settings.common.Notice
import app.nemla.feature.settings.common.error
import app.nemla.feature.settings.common.success
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

internal enum class DebitScope { TODAY, ALL, DATE }

internal sealed interface CardMessage {
    val moved: Int

    data class ImmediateSaved(
        override val moved: Int,
    ) : CardMessage

    data class AppliedFrom(
        val date: LocalDate,
        override val moved: Int,
    ) : CardMessage

    data class AppliedToAll(
        override val moved: Int,
    ) : CardMessage

    data object Failed : CardMessage {
        override val moved: Int = 0
    }
}

internal data class CardUiState(
    val today: LocalDate,
    val language: AppLanguage = AppLanguage.FR,
    val deferred: Boolean = false,
    val bankId: String? = null,
    val cutoffText: String = DeferredDebit.DEFAULT_CUTOFF_DAY.toString(),
    val debitText: String = DeferredDebit.DEFAULT_DEBIT_DAY.toString(),
    val scope: DebitScope = DebitScope.TODAY,
    val date: LocalDate = today,
    val isLoaded: Boolean = false,
    val isSaving: Boolean = false,
    val notice: Notice<CardMessage>? = null,
) {
    val cutoffDay: Int get() = (cutoffText.toIntOrNull() ?: DeferredDebit.DEFAULT_CUTOFF_DAY).coerceIn(Banks.CUTOFF_MIN, Banks.CUTOFF_MAX)

    val debitDay: Int get() = (debitText.toIntOrNull() ?: DeferredDebit.DEFAULT_DEBIT_DAY).coerceIn(Banks.DEBIT_MIN, Banks.DEBIT_MAX)

    val since: LocalDate?
        get() =
            when (scope) {
                DebitScope.TODAY -> today
                DebitScope.ALL -> null
                DebitScope.DATE -> date
            }

    val rule: DeferredDebit get() = DeferredDebit(deferred, cutoffDay, debitDay, if (deferred) since else null)

    val exampleDate: LocalDate get() = DeferredDebit(true, cutoffDay, debitDay, null).debitDateOf(today)

    val canSave: Boolean get() = isLoaded && !isSaving && (!deferred || bankId != null)

    override fun toString(): String = "CardUiState(deferred=$deferred, scope=$scope, isSaving=$isSaving)"
}

internal sealed interface CardAction {
    data object Opened : CardAction

    data class ModeSelected(
        val deferred: Boolean,
    ) : CardAction

    data class BankSelected(
        val id: String,
    ) : CardAction

    data class CutoffChanged(
        val text: String,
    ) : CardAction

    data class DebitChanged(
        val text: String,
    ) : CardAction

    data class ScopeSelected(
        val scope: DebitScope,
    ) : CardAction

    data class DateSelected(
        val date: LocalDate,
    ) : CardAction

    data object SaveClicked : CardAction

    data object NoticeDismissed : CardAction
}

internal fun interface CardClock {
    fun today(): LocalDate
}

internal class CardViewModel(
    private val getSettings: GetDebitSettings,
    private val saveSettings: SaveDebitSettings,
    private val languages: CurrentLanguages,
    private val clock: CardClock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CardUiState(today = clock.today(), language = languages.ui()))
    val state: StateFlow<CardUiState> = mutableState.asStateFlow()

    fun onAction(action: CardAction) {
        when (action) {
            CardAction.Opened -> load()
            is CardAction.ModeSelected -> mutableState.update { it.copy(deferred = action.deferred) }
            is CardAction.BankSelected -> selectBank(action.id)
            is CardAction.CutoffChanged -> if (isDayInput(action.text)) mutableState.update { it.copy(cutoffText = action.text) }
            is CardAction.DebitChanged -> if (isDayInput(action.text)) mutableState.update { it.copy(debitText = action.text) }
            is CardAction.ScopeSelected -> mutableState.update { it.copy(scope = action.scope) }
            is CardAction.DateSelected -> mutableState.update { it.copy(date = minOf(action.date, it.today), scope = DebitScope.DATE) }
            CardAction.SaveClicked -> save()
            CardAction.NoticeDismissed -> mutableState.update { it.copy(notice = null) }
        }
    }

    private fun isDayInput(text: String): Boolean = text.length <= 2 && text.all(Char::isDigit)

    private fun load() {
        val today = clock.today()
        mutableState.update { it.copy(today = today, language = languages.ui()) }
        viewModelScope.launch {
            val result = getSettings()
            if (result is AppResult.Success) {
                val settings = result.value
                val since = settings.rule.since
                mutableState.update {
                    it.copy(
                        deferred = settings.rule.enabled,
                        bankId = Banks.byId(settings.bankId)?.id,
                        cutoffText = settings.rule.cutoffDay.toString(),
                        debitText = settings.rule.debitDay.toString(),
                        scope =
                            when {
                                !settings.choiceMade || !settings.rule.enabled -> DebitScope.TODAY
                                since == null -> DebitScope.ALL
                                else -> DebitScope.DATE
                            },
                        date = since ?: today,
                        isLoaded = true,
                    )
                }
            } else {
                mutableState.update { it.copy(isLoaded = true) }
            }
        }
    }

    private fun selectBank(id: String) {
        val bank = Banks.byId(id) ?: return
        mutableState.update { it.copy(bankId = bank.id, cutoffText = bank.cutoffDay.toString(), debitText = bank.debitDay.toString()) }
    }

    private fun save() {
        val current = mutableState.value
        if (!current.canSave) return
        mutableState.update { it.copy(isSaving = true, notice = null) }
        viewModelScope.launch {
            val result = saveSettings(current.bankId, current.rule)
            val notice =
                when (result) {
                    is AppResult.Success -> {
                        val moved = result.value
                        val since = current.rule.since
                        success<CardMessage>(
                            when {
                                !current.deferred -> CardMessage.ImmediateSaved(moved)
                                since == null -> CardMessage.AppliedToAll(moved)
                                else -> CardMessage.AppliedFrom(since, moved)
                            },
                        )
                    }

                    is AppResult.Failure -> {
                        error<CardMessage>(CardMessage.Failed)
                    }
                }
            mutableState.update { it.copy(isSaving = false, notice = notice) }
        }
    }
}
