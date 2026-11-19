package app.nemla.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import app.nemla.core.designsystem.theme.NemlaTheme

@Stable
class NemlaSnackbars internal constructor(
    internal val hostState: SnackbarHostState,
) {
    suspend fun show(
        message: String,
        actionLabel: String? = null,
    ): Boolean {
        val duration = if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Long
        return hostState.showSnackbar(message = message, actionLabel = actionLabel, duration = duration) ==
            SnackbarResult.ActionPerformed
    }
}

val LocalNemlaSnackbars = staticCompositionLocalOf<NemlaSnackbars?> { null }

@Composable
fun rememberNemlaSnackbars(): NemlaSnackbars = remember { NemlaSnackbars(SnackbarHostState()) }

@Composable
fun NemlaSnackbarHost(
    snackbars: NemlaSnackbars,
    modifier: Modifier = Modifier,
) {
    val colors = NemlaTheme.colors
    SnackbarHost(hostState = snackbars.hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(NemlaTheme.radius.md),
            containerColor = colors.foreground,
            contentColor = colors.background,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
        )
    }
}

@Composable
fun NemlaSnackbarEffect(
    message: String?,
    onShown: () -> Unit,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val snackbars = LocalNemlaSnackbars.current
    val currentOnShown by rememberUpdatedState(onShown)
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(message, snackbars) {
        if (message == null) return@LaunchedEffect
        val acted = snackbars?.show(message, actionLabel) ?: false
        currentOnShown()
        if (acted) currentOnAction()
    }
}
