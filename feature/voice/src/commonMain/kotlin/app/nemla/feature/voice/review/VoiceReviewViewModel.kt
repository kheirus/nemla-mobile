package app.nemla.feature.voice.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nemla.core.common.AppError
import app.nemla.core.common.AppResult
import app.nemla.core.domain.model.AmountInput
import app.nemla.core.domain.model.Category
import app.nemla.core.domain.model.ExpenseDraft
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ReviewDecision
import app.nemla.core.domain.model.VoiceCapture
import app.nemla.core.domain.usecase.ConfirmReviewedExpenses
import app.nemla.core.domain.usecase.DeletePendingExpense
import app.nemla.core.domain.usecase.FindLearnedExpenses
import app.nemla.core.domain.usecase.ListCategories
import app.nemla.feature.voice.ReviewOutcome
import app.nemla.feature.voice.Today
import app.nemla.feature.voice.VoiceReviewStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

internal class VoiceReviewViewModel(
    private val store: VoiceReviewStore,
    private val listCategories: ListCategories,
    private val findLearned: FindLearnedExpenses,
    private val confirmReviewed: ConfirmReviewedExpenses,
    private val deletePending: DeletePendingExpense,
    private val today: Today,
    private val decimalSeparator: Char,
) : ViewModel() {
    private val capture: VoiceCapture? = store.pending.value
    private val drafts: Map<ExpenseId, ExpenseDraft> =
        capture
            ?.drafts
            ?.mapNotNull { draft -> draft.id?.let { it to draft } }
            ?.toMap()
            .orEmpty()
    private var categories: List<Category> = emptyList()

    private val mutableState =
        MutableStateFlow(
            VoiceReviewUiState(
                today = today.date(),
                isLoading = capture != null,
                transcript = capture?.transcript?.takeIf { it.isNotBlank() },
                lines = drafts.values.map { it.toLine(today.date()) },
                detectedCount = store.takeAnnouncement(),
            ),
        )
    val state: StateFlow<VoiceReviewUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<VoiceReviewEffect>(Channel.BUFFERED)
    val effects: Flow<VoiceReviewEffect> = effectChannel.receiveAsFlow()

    init {
        if (capture != null) load(capture)
    }

    fun onAction(action: VoiceReviewAction) {
        when (action) {
            is VoiceReviewAction.AmountChanged -> editAmount(action.id, action.text)
            is VoiceReviewAction.CategorySelected -> updateLine(action.id) { it.copy(categoryId = action.categoryId) }
            is VoiceReviewAction.DateSelected -> updateLine(action.id) { it.copy(date = clamp(action.date)) }
            is VoiceReviewAction.DeleteClicked -> delete(action.id)
            VoiceReviewAction.ConfirmClicked -> confirm()
            VoiceReviewAction.LaterClicked -> close()
            VoiceReviewAction.MessageDismissed -> mutableState.update { it.copy(message = null, detectedCount = null) }
        }
    }

    private fun load(capture: VoiceCapture) {
        viewModelScope.launch {
            val owned =
                when (val result = listCategories()) {
                    is AppResult.Success -> result.value
                    is AppResult.Failure -> null
                }
            categories = owned.orEmpty()
            val learned = findLearned(capture.drafts)
            val known = categories.map { it.id }.toSet()
            mutableState.update { current ->
                current.copy(
                    isLoading = false,
                    categories = categories.map { CategoryChoice(it.id, it.name) },
                    lines =
                        current.lines.map { line ->
                            line.copy(
                                learned = line.id in learned,
                                categoryId = line.categoryId?.takeIf { owned == null || it in known },
                            )
                        },
                    message = if (owned == null) ReviewMessage.CATEGORIES_UNAVAILABLE else current.message,
                )
            }
        }
    }

    private fun editAmount(
        id: ExpenseId,
        text: String,
    ) {
        if (!AmountInput.isEditable(text)) return
        updateLine(id) { it.copy(amountText = text) }
    }

    private fun updateLine(
        id: ExpenseId,
        transform: (ReviewLine) -> ReviewLine,
    ) {
        if (mutableState.value.isSaving) return
        mutableState.update { current -> current.copy(lines = current.lines.map { if (it.id == id) transform(it) else it }) }
    }

    private fun clamp(date: LocalDate): LocalDate {
        val now = today.date()
        return if (date > now) now else date
    }

    private fun delete(id: ExpenseId) {
        val current = mutableState.value
        if (current.isSaving || current.lines.none { it.id == id && !it.isDeleting }) return
        updateLine(id) { it.copy(isDeleting = true) }
        viewModelScope.launch {
            when (deletePending(id)) {
                is AppResult.Success -> {
                    store.remove(id)
                    mutableState.update { state -> state.copy(lines = state.lines.filterNot { it.id == id }) }
                }

                is AppResult.Failure -> {
                    mutableState.update { state ->
                        state.copy(
                            lines = state.lines.map { if (it.id == id) it.copy(isDeleting = false) else it },
                            message = ReviewMessage.DELETE_FAILED,
                        )
                    }
                }
            }
        }
    }

    private fun confirm() {
        val current = mutableState.value
        if (!current.canConfirm) return
        val decisions =
            current.lines.mapNotNull { line ->
                val draft = drafts[line.id] ?: return@mapNotNull null
                ReviewDecision(
                    draft = draft,
                    amountCents = AmountInput.parseCents(line.amountText) ?: 0,
                    date = clamp(line.date),
                    categoryId = line.categoryId,
                )
            }
        mutableState.update { it.copy(isSaving = true, message = null, detectedCount = null) }
        viewModelScope.launch {
            when (val result = confirmReviewed(capture?.entryId, decisions, categories)) {
                is AppResult.Success -> {
                    store.clear()
                    store.publish(ReviewOutcome.SAVED)
                    mutableState.update { it.copy(isSaving = false) }
                    effectChannel.send(VoiceReviewEffect.Close)
                }

                is AppResult.Failure -> {
                    mutableState.update { it.copy(isSaving = false, message = result.error.toReviewMessage()) }
                }
            }
        }
    }

    private fun close() {
        if (mutableState.value.isSaving) return
        viewModelScope.launch { effectChannel.send(VoiceReviewEffect.Close) }
    }

    private fun ExpenseDraft.toLine(today: LocalDate): ReviewLine {
        val id = checkNotNull(id)
        return ReviewLine(
            id = id,
            label = label,
            amountText = AmountInput.format(amountCents, decimalSeparator),
            date = if (date > today) today else date,
            categoryId = categoryId,
        )
    }
}

private fun AppError.toReviewMessage(): ReviewMessage =
    when (this) {
        AppError.Network.Offline -> ReviewMessage.OFFLINE
        AppError.Network.Timeout -> ReviewMessage.TIMEOUT
        else -> ReviewMessage.SAVE_FAILED
    }
