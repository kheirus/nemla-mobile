package app.nemla.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.nemla.core.domain.model.PendingReview
import app.nemla.feature.dashboard.ui.DashboardRoute
import kotlinx.serialization.Serializable

@Serializable
data object DashboardDestination

fun NavGraphBuilder.dashboardScreen(
    onSignedOut: () -> Unit,
    onReviewPending: (PendingReview) -> Unit,
    onOpenSettings: (categories: Boolean) -> Unit,
    floatingAction: @Composable (expanded: Boolean) -> Unit = {},
    reportNotice: @Composable () -> Unit = {},
    notificationBanners: @Composable () -> Unit = {},
) {
    composable<DashboardDestination> {
        DashboardRoute(
            onSignedOut = onSignedOut,
            onReviewPending = onReviewPending,
            onOpenSettings = onOpenSettings,
            floatingAction = floatingAction,
            reportNotice = reportNotice,
            notificationBanners = notificationBanners,
        )
    }
}

fun NavController.navigateToDashboard() {
    navigate(DashboardDestination) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

fun NavDestination.isDashboard(): Boolean = hasRoute<DashboardDestination>()
