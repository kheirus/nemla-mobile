package app.nemla.feature.settings

import androidx.compose.runtime.Composable
import app.nemla.core.domain.model.Currency
import app.nemla.core.domain.model.GlobalBudget
import app.nemla.core.domain.model.Household

typealias SettingsDictationSlot = @Composable (
    enabled: Boolean,
    onTranscript: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
) -> Unit

enum class SettingsTab { PROFILE, REPORTS, CREDITS, CATEGORIES }

internal enum class SettingsPanel { BUDGET, RECURRING, PROFILE, PREFERENCES, CARD }

internal enum class AccountMessage { DELETE_UNAVAILABLE, DELETE_FAILED, SIGN_OUT_FAILED }

internal data class SettingsUiState(
    val tab: SettingsTab = SettingsTab.PROFILE,
    val panel: SettingsPanel? = null,
    val pseudo: String? = null,
    val household: Household? = null,
    val currency: Currency = Currency.Default,
    val budget: GlobalBudget? = null,
    val isConfirmingDelete: Boolean = false,
    val isDeleting: Boolean = false,
    val isSigningOut: Boolean = false,
    val accountMessage: AccountMessage? = null,
) {
    override fun toString(): String = "SettingsUiState(tab=$tab, panel=$panel, isDeleting=$isDeleting)"
}

internal sealed interface SettingsAction {
    data object Started : SettingsAction

    data class TabSelected(
        val tab: SettingsTab,
    ) : SettingsAction

    data class PanelOpened(
        val panel: SettingsPanel,
    ) : SettingsAction

    data object BackClicked : SettingsAction

    data object ProfileChanged : SettingsAction

    data object CategoryBudgetsRequested : SettingsAction

    data object DeleteAccountClicked : SettingsAction

    data object DeleteAccountConfirmed : SettingsAction

    data object DeleteAccountCancelled : SettingsAction

    data object AccountMessageDismissed : SettingsAction

    data object SignOutClicked : SettingsAction
}

internal sealed interface SettingsEffect {
    data object Close : SettingsEffect

    data object SignedOut : SettingsEffect
}
