package app.nemla.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.nemla.core.designsystem.brand.NemlaLogo
import app.nemla.core.designsystem.brand.NemlaLogoSize
import app.nemla.core.designsystem.theme.NemlaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Stable
class NemlaTopBarScrollBehavior internal constructor(
    internal val behavior: TopAppBarScrollBehavior,
) {
    val nestedScrollConnection: NestedScrollConnection get() = behavior.nestedScrollConnection
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberNemlaTopBarScrollBehavior(): NemlaTopBarScrollBehavior {
    val behavior = TopAppBarDefaults.pinnedScrollBehavior()
    return remember(behavior) { NemlaTopBarScrollBehavior(behavior) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NemlaTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: NemlaTopBarScrollBehavior? = null,
) {
    val colors = NemlaTheme.colors
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = colors.background,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                navigationIconContentColor = colors.foreground,
                titleContentColor = colors.foreground,
                actionIconContentColor = colors.foreground,
            ),
        scrollBehavior = scrollBehavior?.behavior,
    )
}

@Composable
fun NemlaTopBarTitle(text: String) {
    Text(
        text = text,
        style = NemlaTheme.typography.sectionTitle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
fun NemlaTopBarBrand() {
    NemlaLogo(size = NemlaLogoSize.Header)
}
