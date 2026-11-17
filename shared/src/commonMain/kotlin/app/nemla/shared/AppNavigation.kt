package app.nemla.shared

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import app.nemla.core.designsystem.component.NemlaNavigationBar
import app.nemla.core.designsystem.component.NemlaNavigationItem
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.feature.dashboard.DashboardDestination
import app.nemla.feature.dashboard.isDashboard
import app.nemla.feature.settings.SettingsDestination
import app.nemla.feature.settings.SettingsTab
import app.nemla.shared.resources.Res
import app.nemla.shared.resources.app_tab_home
import app.nemla.shared.resources.app_tab_reports
import app.nemla.shared.resources.app_tab_settings
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

private const val SLIDE_MS = 300
private const val FADE_MS = 220
private const val SLIDE_DIVISOR = 5

@Serializable
internal data object ReportsDestination

internal enum class AppTab { HOME, REPORTS, SETTINGS }

internal fun NavDestination.topLevelTab(): AppTab? =
    when {
        isDashboard() -> AppTab.HOME
        hasRoute<ReportsDestination>() -> AppTab.REPORTS
        hasRoute<SettingsDestination>() -> AppTab.SETTINGS
        else -> null
    }

internal fun NavHostController.navigateToTab(
    tab: AppTab,
    settingsTab: SettingsTab? = null,
) {
    val route: Any =
        when (tab) {
            AppTab.HOME -> DashboardDestination
            AppTab.REPORTS -> ReportsDestination
            AppTab.SETTINGS -> SettingsDestination((settingsTab ?: SettingsTab.PROFILE).ordinal)
        }
    navigate(route) {
        popUpTo<DashboardDestination> { saveState = true }
        launchSingleTop = true
        restoreState = settingsTab == null
    }
}

@Composable
internal fun AppNavigationBar(
    selected: AppTab?,
    onSelect: (AppTab) -> Unit,
) {
    NemlaNavigationBar(
        items =
            listOf(
                NemlaNavigationItem(AppTab.HOME, stringResource(Res.string.app_tab_home), NemlaIcons.House),
                NemlaNavigationItem(AppTab.REPORTS, stringResource(Res.string.app_tab_reports), NemlaIcons.BarChart),
                NemlaNavigationItem(AppTab.SETTINGS, stringResource(Res.string.app_tab_settings), NemlaIcons.UserRound),
            ),
        selected = selected,
        onSelect = onSelect,
    )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.fadesOnly(): Boolean {
    val from = initialState.destination
    val to = targetState.destination
    if (from.hasRoute<SplashDestination>() || to.hasRoute<SplashDestination>()) return true
    return ActiveChromeStyle == AppChromeStyle.BottomNavigation && from.topLevelTab() != null && to.topLevelTab() != null
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.appEnter(): EnterTransition =
    if (fadesOnly()) {
        fadeIn(tween(FADE_MS))
    } else {
        slideInHorizontally(tween(SLIDE_MS)) { it / SLIDE_DIVISOR } + fadeIn(tween(SLIDE_MS))
    }

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.appExit(): ExitTransition =
    if (fadesOnly()) {
        fadeOut(tween(FADE_MS))
    } else {
        slideOutHorizontally(tween(SLIDE_MS)) { -it / SLIDE_DIVISOR } + fadeOut(tween(SLIDE_MS))
    }

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.appPopEnter(): EnterTransition =
    if (fadesOnly()) {
        fadeIn(tween(FADE_MS))
    } else {
        slideInHorizontally(tween(SLIDE_MS)) { -it / SLIDE_DIVISOR } + fadeIn(tween(SLIDE_MS))
    }

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.appPopExit(): ExitTransition =
    if (fadesOnly()) {
        fadeOut(tween(FADE_MS))
    } else {
        slideOutHorizontally(tween(SLIDE_MS)) { it / SLIDE_DIVISOR } + fadeOut(tween(SLIDE_MS))
    }
