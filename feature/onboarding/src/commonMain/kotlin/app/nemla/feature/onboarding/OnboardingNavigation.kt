package app.nemla.feature.onboarding

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingDestination

fun NavGraphBuilder.onboardingScreen(
    onFinished: () -> Unit,
    onSignedOut: () -> Unit,
    dictation: OnboardingDictationSlot,
) {
    composable<OnboardingDestination> {
        OnboardingRoute(onFinished = onFinished, onSignedOut = onSignedOut, dictation = dictation)
    }
}

fun NavController.navigateToOnboarding() {
    navigate(OnboardingDestination) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

fun NavDestination.isOnboarding(): Boolean = hasRoute<OnboardingDestination>()
