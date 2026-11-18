package app.nemla.shared

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute

private const val SLIDE_MS = 300
private const val FADE_MS = 220
private const val SLIDE_DIVISOR = 5

private fun AnimatedContentTransitionScope<NavBackStackEntry>.fadesOnly(): Boolean =
    initialState.destination.hasRoute<SplashDestination>() || targetState.destination.hasRoute<SplashDestination>()

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
