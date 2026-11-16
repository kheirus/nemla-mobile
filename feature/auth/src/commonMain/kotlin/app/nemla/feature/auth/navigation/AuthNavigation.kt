package app.nemla.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import app.nemla.feature.auth.forgot.ForgotPasswordRoute
import app.nemla.feature.auth.signin.SignInRoute
import app.nemla.feature.auth.signup.SignUpRoute
import kotlinx.serialization.Serializable

@Serializable
data object AuthGraphDestination

@Serializable
internal data class SignInDestination(
    val resetLinkSent: Boolean = false,
)

@Serializable
internal data object SignUpDestination

@Serializable
internal data object ForgotPasswordDestination

enum class AuthEntry { SignIn, SignUp }

fun NavGraphBuilder.authGraph(
    navController: NavController,
    onAuthenticated: () -> Unit,
) {
    navigation<AuthGraphDestination>(startDestination = SignUpDestination) {
        composable<SignUpDestination> {
            SignUpRoute(
                onSignedUp = onAuthenticated,
                onOpenSignIn = {
                    navController.navigate(SignInDestination()) { replace<SignUpDestination>() }
                },
            )
        }
        composable<SignInDestination> { entry ->
            val destination = entry.toRoute<SignInDestination>()
            SignInRoute(
                resetLinkSent = destination.resetLinkSent,
                onSignedIn = onAuthenticated,
                onOpenForgotPassword = { navController.navigate(ForgotPasswordDestination) { launchSingleTop = true } },
                onOpenSignUp = {
                    navController.navigate(SignUpDestination) { replace<SignInDestination>() }
                },
            )
        }
        composable<ForgotPasswordDestination> {
            ForgotPasswordRoute(
                onLinkSent = {
                    navController.navigate(SignInDestination(resetLinkSent = true)) { replace<SignInDestination>() }
                },
                onOpenSignUp = {
                    navController.navigate(SignUpDestination) { replace<SignInDestination>() }
                },
            )
        }
    }
}

fun NavController.navigateToAuth(entry: AuthEntry) {
    val destination: Any =
        when (entry) {
            AuthEntry.SignIn -> SignInDestination()
            AuthEntry.SignUp -> SignUpDestination
        }
    navigate(destination) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

fun NavDestination.isInAuthGraph(): Boolean = hierarchy.any { it.hasRoute<AuthGraphDestination>() }

private inline fun <reified T : Any> NavOptionsBuilder.replace() {
    popUpTo<T> { inclusive = true }
    launchSingleTop = true
}
