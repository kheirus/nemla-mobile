package app.nemla.feature.onboarding

import androidx.compose.runtime.Composable
import app.nemla.core.domain.model.CategoryId
import app.nemla.core.domain.model.ChargeFrequency
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.Household

typealias OnboardingDictationSlot = @Composable (
    enabled: Boolean,
    onTranscript: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
) -> Unit

internal enum class OnboardingMessage { ANALYSIS_FAILED, NOTHING_DETECTED, SAVE_FAILED, OFFLINE }

internal sealed interface OnboardingNotice {
    data class Known(
        val message: OnboardingMessage,
    ) : OnboardingNotice

    class Dictation(
        val text: String,
    ) : OnboardingNotice {
        override fun toString(): String = "Dictation(***)"
    }
}

internal data class CategoryChoice(
    val id: CategoryId,
    val name: String,
)

internal data class ChargeRow(
    val key: Int,
    val name: String,
    val amountText: String,
    val categoryId: CategoryId?,
    val frequency: ChargeFrequency,
    val dayOfMonth: Int,
) {
    override fun toString(): String = "ChargeRow(key=$key, frequency=$frequency)"
}

internal data class OnboardingUiState(
    val pseudo: String? = null,
    val household: Household? = null,
    val currency: Currency = Currency.Default,
    val text: String = "",
    val isAnalyzing: Boolean = false,
    val isDictating: Boolean = false,
    val categories: List<CategoryChoice> = emptyList(),
    val rows: List<ChargeRow> = emptyList(),
    val budgetText: String = "",
    val isSaving: Boolean = false,
    val isSigningOut: Boolean = false,
    val notice: OnboardingNotice? = null,
) {
    val isBusy: Boolean get() = isAnalyzing || isDictating || isSaving

    val canAnalyze: Boolean get() = !isBusy && text.trim().length >= MIN_TEXT_LENGTH

    override fun toString(): String = "OnboardingUiState(rows=${rows.size}, isAnalyzing=$isAnalyzing, isSaving=$isSaving)"

    companion object {
        const val MIN_TEXT_LENGTH: Int = 3
    }
}

internal sealed interface OnboardingAction {
    data class TextChanged(
        val text: String,
    ) : OnboardingAction {
        override fun toString(): String = "TextChanged(***)"
    }

    data object AnalyzeClicked : OnboardingAction

    data class DictationActiveChanged(
        val active: Boolean,
    ) : OnboardingAction

    class Transcribed(
        val text: String,
    ) : OnboardingAction {
        override fun toString(): String = "Transcribed(***)"
    }

    class DictationMessage(
        val text: String,
    ) : OnboardingAction

    data class RowNameChanged(
        val key: Int,
        val name: String,
    ) : OnboardingAction {
        override fun toString(): String = "RowNameChanged(key=$key)"
    }

    data class RowAmountChanged(
        val key: Int,
        val text: String,
    ) : OnboardingAction {
        override fun toString(): String = "RowAmountChanged(key=$key)"
    }

    data class RowCategorySelected(
        val key: Int,
        val id: CategoryId,
    ) : OnboardingAction

    data class RowFrequencySelected(
        val key: Int,
        val frequency: ChargeFrequency,
    ) : OnboardingAction

    data class RowRemoved(
        val key: Int,
    ) : OnboardingAction

    data class BudgetChanged(
        val text: String,
    ) : OnboardingAction {
        override fun toString(): String = "BudgetChanged(***)"
    }

    data object SaveClicked : OnboardingAction

    data object NoticeDismissed : OnboardingAction

    data object SignOutClicked : OnboardingAction
}

internal sealed interface OnboardingEffect {
    data object Finished : OnboardingEffect

    data object SignedOut : OnboardingEffect
}
