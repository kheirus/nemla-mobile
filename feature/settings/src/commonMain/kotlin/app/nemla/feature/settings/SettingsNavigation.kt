package app.nemla.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.nemla.feature.settings.budget.BudgetPanelRoute
import app.nemla.feature.settings.card.CardPanelRoute
import app.nemla.feature.settings.preferences.PreferencesPanelRoute
import app.nemla.feature.settings.profile.ProfilePanelRoute
import app.nemla.feature.settings.recurring.RecurringPanelRoute
import app.nemla.feature.settings.resources.Res
import app.nemla.feature.settings.resources.settings_entry_budget
import app.nemla.feature.settings.resources.settings_entry_card
import app.nemla.feature.settings.resources.settings_entry_preferences
import app.nemla.feature.settings.resources.settings_entry_profile
import app.nemla.feature.settings.resources.settings_entry_recurring
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

@Serializable
data class SettingsDestination(
    val tab: Int = SettingsTab.PROFILE.ordinal,
)

@Serializable
internal data object SettingsBudgetDestination

@Serializable
internal data object SettingsRecurringDestination

@Serializable
internal data object SettingsProfileDestination

@Serializable
internal data object SettingsPreferencesDestination

@Serializable
internal data object SettingsCardDestination

private const val REQUESTED_TAB_KEY = "settings.requestedTab"

fun NavGraphBuilder.settingsScreen(
    navController: NavController,
    onClose: () -> Unit,
    onSignedOut: () -> Unit,
    dictation: SettingsDictationSlot,
    reportHistory: @Composable () -> Unit,
    topLevel: Boolean = false,
) {
    composable<SettingsDestination> { entry ->
        val tab = SettingsTab.entries.getOrElse(entry.toRoute<SettingsDestination>().tab) { SettingsTab.PROFILE }
        val requested by entry.savedStateHandle.getStateFlow<Int?>(REQUESTED_TAB_KEY, null).collectAsStateWithLifecycle()
        SettingsRoute(
            initialTab = tab,
            requestedTab = requested?.let { SettingsTab.entries.getOrNull(it) },
            onRequestedTabShown = { entry.savedStateHandle.remove<Int>(REQUESTED_TAB_KEY) },
            onOpenPanel = { panel -> navController.navigate(panel.destination()) },
            onClose = onClose,
            onSignedOut = onSignedOut,
            reportHistory = reportHistory,
            topLevel = topLevel,
        )
    }
    composable<SettingsBudgetDestination> {
        SettingsPanelScreen(stringResource(Res.string.settings_entry_budget), onBack = dropUnlessResumed { navController.popBackStack() }) {
            BudgetPanelRoute(onOpenCategories = dropUnlessResumed { navController.showSettingsTab(SettingsTab.CATEGORIES) })
        }
    }
    composable<SettingsRecurringDestination> {
        SettingsPanelScreen(
            stringResource(Res.string.settings_entry_recurring),
            onBack = dropUnlessResumed { navController.popBackStack() },
        ) {
            RecurringPanelRoute(dictation = dictation)
        }
    }
    composable<SettingsProfileDestination> {
        SettingsPanelScreen(
            stringResource(Res.string.settings_entry_profile),
            onBack = dropUnlessResumed { navController.popBackStack() },
        ) {
            ProfilePanelRoute()
        }
    }
    composable<SettingsPreferencesDestination> {
        SettingsPanelScreen(
            stringResource(Res.string.settings_entry_preferences),
            onBack = dropUnlessResumed { navController.popBackStack() },
        ) {
            PreferencesPanelRoute()
        }
    }
    composable<SettingsCardDestination> {
        SettingsPanelScreen(stringResource(Res.string.settings_entry_card), onBack = dropUnlessResumed { navController.popBackStack() }) {
            CardPanelRoute()
        }
    }
}

private fun NavController.showSettingsTab(tab: SettingsTab) {
    val settings = runCatching { getBackStackEntry<SettingsDestination>() }.getOrNull()
    if (settings == null) {
        popBackStack()
        navigateToSettings(tab)
        return
    }
    settings.savedStateHandle[REQUESTED_TAB_KEY] = tab.ordinal
    popBackStack<SettingsDestination>(inclusive = false)
}

internal fun SettingsPanel.destination(): Any =
    when (this) {
        SettingsPanel.BUDGET -> SettingsBudgetDestination
        SettingsPanel.RECURRING -> SettingsRecurringDestination
        SettingsPanel.PROFILE -> SettingsProfileDestination
        SettingsPanel.PREFERENCES -> SettingsPreferencesDestination
        SettingsPanel.CARD -> SettingsCardDestination
    }

fun NavController.navigateToSettings(tab: SettingsTab = SettingsTab.PROFILE) {
    navigate(SettingsDestination(tab.ordinal)) { launchSingleTop = true }
}
