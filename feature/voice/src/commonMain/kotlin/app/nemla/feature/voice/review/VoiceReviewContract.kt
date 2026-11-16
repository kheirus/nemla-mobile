package app.nemla.feature.voice.review

import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.ExpenseId
import app.nemla.core.domain.model.ReviewOrigin
import kotlinx.datetime.LocalDate

internal data class ReviewLine(
    val id: ExpenseId,
    val label: String,
    val amountText: String,
    val date: LocalDate,
    val categoryId: CategoryId?,
    val learned: Boolean = false,
    val isDeleting: Boolean = false,
) {
    override fun toString(): String = "ReviewLine(id=${id.value}, learned=$learned, isDeleting=$isDeleting)"
}

internal data class CategoryChoice(
    val id: CategoryId,
    val name: String,
)

internal enum class ReviewMessage { SAVE_FAILED, DELETE_FAILED, CATEGORIES_UNAVAILABLE, OFFLINE, TIMEOUT }

internal data class VoiceReviewUiState(
    val today: LocalDate,
    val origin: ReviewOrigin = ReviewOrigin.VOICE,
    val isLoading: Boolean = true,
    val transcript: String? = null,
    val lines: List<ReviewLine> = emptyList(),
    val categories: List<CategoryChoice> = emptyList(),
    val detectedCount: Int? = null,
    val isSaving: Boolean = false,
    val message: ReviewMessage? = null,
) {
    val canConfirm: Boolean get() = !isLoading && !isSaving && lines.isNotEmpty() && lines.none { it.isDeleting }

    override fun toString(): String =
        "VoiceReviewUiState(origin=$origin, isLoading=$isLoading, lines=${lines.size}, isSaving=$isSaving, message=$message)"
}

internal sealed interface VoiceReviewAction {
    data class AmountChanged(
        val id: ExpenseId,
        val text: String,
    ) : VoiceReviewAction {
        override fun toString(): String = "AmountChanged(id=${id.value})"
    }

    data class CategorySelected(
        val id: ExpenseId,
        val categoryId: CategoryId,
    ) : VoiceReviewAction

    data class DateSelected(
        val id: ExpenseId,
        val date: LocalDate,
    ) : VoiceReviewAction

    data class DeleteClicked(
        val id: ExpenseId,
    ) : VoiceReviewAction

    data object ConfirmClicked : VoiceReviewAction

    data object LaterClicked : VoiceReviewAction

    data object MessageDismissed : VoiceReviewAction
}

internal sealed interface VoiceReviewEffect {
    data object Close : VoiceReviewEffect
}
