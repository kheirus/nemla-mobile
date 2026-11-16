package app.nemla.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AmountInput
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.RecurringChargeDraft
import app.nemla.core.domain.usecase.CompleteOnboarding
import app.nemla.core.domain.usecase.CurrentLanguages
import app.nemla.core.domain.usecase.GetUserProfile
import app.nemla.core.domain.usecase.ParseRecurringCharges
import app.nemla.core.domain.usecase.PrepareOnboarding
import app.nemla.core.domain.usecase.SignOut
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class OnboardingViewModel(
    private val prepare: PrepareOnboarding,
    private val getProfile: GetUserProfile,
    private val parseCharges: ParseRecurringCharges,
    private val complete: CompleteOnboarding,
    private val signOut: SignOut,
    private val languages: CurrentLanguages,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = effectChannel.receiveAsFlow()

    private var nextKey = 0
    private var analysisJob: Job? = null

    private val decimalSeparator: Char get() = if (languages.ui() == AppLanguage.EN) '.' else ','

    init {
        viewModelScope.launch {
            val categories = prepare()
            if (categories is AppResult.Success) {
                mutableState.update { state -> state.copy(categories = categories.value.map { CategoryChoice(it.id, it.name) }) }
            }
            val profile = getProfile()
            if (profile is AppResult.Success) {
                mutableState.update {
                    it.copy(
                        pseudo =
                            profile.value.pseudo
                                ?.trim()
                                ?.takeIf(String::isNotEmpty),
                        household = profile.value.household,
                        currency = profile.value.currency,
                    )
                }
            }
        }
    }

    fun onAction(action: OnboardingAction) {
        when (action) {
            is OnboardingAction.TextChanged -> {
                mutableState.update { it.copy(text = action.text) }
            }

            OnboardingAction.AnalyzeClicked -> {
                if (mutableState.value.canAnalyze) analyze(mutableState.value.text)
            }

            is OnboardingAction.DictationActiveChanged -> {
                mutableState.update { it.copy(isDictating = action.active) }
            }

            is OnboardingAction.Transcribed -> {
                onTranscribed(action.text)
            }

            is OnboardingAction.DictationMessage -> {
                mutableState.update { it.copy(notice = OnboardingNotice.Dictation(action.text)) }
            }

            is OnboardingAction.RowNameChanged -> {
                updateRow(action.key) { it.copy(name = action.name) }
            }

            is OnboardingAction.RowAmountChanged -> {
                if (AmountInput.isEditable(
                        action.text,
                    )
                ) {
                    updateRow(action.key) { it.copy(amountText = action.text) }
                }
            }

            is OnboardingAction.RowCategorySelected -> {
                updateRow(action.key) { it.copy(categoryId = action.id) }
            }

            is OnboardingAction.RowFrequencySelected -> {
                updateRow(action.key) { it.copy(frequency = action.frequency) }
            }

            is OnboardingAction.RowRemoved -> {
                mutableState.update { state ->
                    state.copy(rows = state.rows.filterNot { it.key == action.key })
                }
            }

            is OnboardingAction.BudgetChanged -> {
                if (AmountInput.isEditable(
                        action.text,
                    )
                ) {
                    mutableState.update { it.copy(budgetText = action.text) }
                }
            }

            OnboardingAction.SaveClicked -> {
                save()
            }

            OnboardingAction.NoticeDismissed -> {
                mutableState.update { it.copy(notice = null) }
            }

            OnboardingAction.SignOutClicked -> {
                signOutOnce()
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
        analysisJob?.cancel()
        mutableState.update { it.copy(isAnalyzing = true, notice = null) }
        analysisJob =
            viewModelScope.launch {
                when (val result = parseCharges(text, languages.dictation())) {
                    is AppResult.Success -> {
                        val rows = result.value.map(::toRow)
                        mutableState.update {
                            it.copy(
                                isAnalyzing = false,
                                rows = rows,
                                notice = if (rows.isEmpty()) OnboardingNotice.Known(OnboardingMessage.NOTHING_DETECTED) else null,
                            )
                        }
                    }

                    is AppResult.Failure -> {
                        mutableState.update {
                            it.copy(
                                isAnalyzing = false,
                                notice = OnboardingNotice.Known(result.error.toMessage(OnboardingMessage.ANALYSIS_FAILED)),
                            )
                        }
                    }
                }
            }
    }

    private fun toRow(draft: RecurringChargeDraft): ChargeRow =
        ChargeRow(
            key = nextKey++,
            name = draft.name,
            amountText = AmountInput.format(draft.amountCents, decimalSeparator),
            categoryId = draft.categoryId?.takeIf { id -> mutableState.value.categories.any { it.id == id } },
            frequency = draft.frequency,
            dayOfMonth = draft.dayOfMonth,
        )

    private fun updateRow(
        key: Int,
        transform: (ChargeRow) -> ChargeRow,
    ) {
        mutableState.update { state -> state.copy(rows = state.rows.map { if (it.key == key) transform(it) else it }) }
    }

    private fun save() {
        val current = mutableState.value
        if (current.isSaving) return
        val drafts =
            current.rows.map { row ->
                RecurringChargeDraft(
                    name = row.name,
                    amountCents = AmountInput.parseCents(row.amountText)?.coerceAtLeast(0) ?: 0,
                    frequency = row.frequency,
                    dayOfMonth = row.dayOfMonth,
                    categoryId = row.categoryId,
                )
            }
        val budget = AmountInput.parseCents(current.budgetText)
        mutableState.update { it.copy(isSaving = true, notice = null) }
        viewModelScope.launch {
            when (val result = complete(drafts, budget)) {
                is AppResult.Success -> {
                    mutableState.update { it.copy(isSaving = false) }
                    effectChannel.send(OnboardingEffect.Finished)
                }

                is AppResult.Failure -> {
                    mutableState.update {
                        it.copy(isSaving = false, notice = OnboardingNotice.Known(result.error.toMessage(OnboardingMessage.SAVE_FAILED)))
                    }
                }
            }
        }
    }

    private fun signOutOnce() {
        if (mutableState.value.isSigningOut) return
        mutableState.update { it.copy(isSigningOut = true) }
        viewModelScope.launch {
            val result = signOut()
            mutableState.update { it.copy(isSigningOut = false) }
            if (result is AppResult.Success) effectChannel.send(OnboardingEffect.SignedOut)
        }
    }
}

private fun AppError.toMessage(fallback: OnboardingMessage): OnboardingMessage =
    when (this) {
        AppError.Network.Offline -> OnboardingMessage.OFFLINE
        else -> fallback
    }
