package app.nemla.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.nemla.core.designsystem.theme.NemlaTheme

@Composable
fun NemlaScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    scrollBehavior: NemlaTopBarScrollBehavior? = null,
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = NemlaTheme.colors
    val snackbars = rememberNemlaSnackbars()
    val scrollModifier = scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) } ?: Modifier
    CompositionLocalProvider(LocalNemlaSnackbars provides snackbars) {
        Scaffold(
            modifier = modifier.then(scrollModifier),
            topBar = topBar,
            bottomBar = bottomBar,
            snackbarHost = { NemlaSnackbarHost(snackbars) },
            floatingActionButton = floatingActionButton,
            containerColor = colors.background,
            contentColor = colors.foreground,
            contentWindowInsets = contentWindowInsets,
            content = content,
        )
    }
}
