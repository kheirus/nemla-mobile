package app.nemla.feature.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data class SettingsDestination(
    val tab: Int = SettingsTab.PROFILE.ordinal,
)

fun NavGraphBuilder.settingsScreen(
    onClose: () -> Unit,
    onSignedOut: () -> Unit,
    dictation: SettingsDictationSlot,
    reportHistory: @Composable () -> Unit,
) {
    composable<SettingsDestination> { entry ->
        val tab = SettingsTab.entries.getOrElse(entry.toRoute<SettingsDestination>().tab) { SettingsTab.PROFILE }
        SettingsRoute(
            initialTab = tab,
            onClose = onClose,
            onSignedOut = onSignedOut,
            dictation = dictation,
            reportHistory = reportHistory,
        )
    }
}

fun NavController.navigateToSettings(tab: SettingsTab = SettingsTab.PROFILE) {
    navigate(SettingsDestination(tab.ordinal)) { launchSingleTop = true }
}
