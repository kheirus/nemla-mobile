package app.nemla.feature.home

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object HomeDestination

fun NavGraphBuilder.homeScreen(
    onSignedOut: () -> Unit,
    floatingAction: @Composable () -> Unit = {},
) {
    composable<HomeDestination> { HomeRoute(onSignedOut = onSignedOut, floatingAction = floatingAction) }
}

fun NavController.navigateToHome() {
    navigate(HomeDestination) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

fun NavDestination.isHome(): Boolean = hasRoute<HomeDestination>()
