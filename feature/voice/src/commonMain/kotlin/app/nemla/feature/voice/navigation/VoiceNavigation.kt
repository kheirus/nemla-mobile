package app.nemla.feature.voice.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.nemla.feature.voice.review.VoiceReviewRoute
import kotlinx.serialization.Serializable

@Serializable
data object VoiceReviewDestination

fun NavGraphBuilder.voiceGraph(onClose: () -> Unit) {
    composable<VoiceReviewDestination> { VoiceReviewRoute(onClose = onClose) }
}

fun NavController.navigateToVoiceReview() {
    navigate(VoiceReviewDestination) { launchSingleTop = true }
}

fun NavController.closeVoiceReview() {
    if (currentDestination?.hasRoute<VoiceReviewDestination>() == true) popBackStack()
}
