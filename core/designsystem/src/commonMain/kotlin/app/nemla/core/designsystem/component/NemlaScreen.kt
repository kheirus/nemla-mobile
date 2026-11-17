package app.nemla.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.nemla.core.designsystem.resources.Res
import app.nemla.core.designsystem.resources.ds_loading
import app.nemla.core.designsystem.theme.NemlaTheme
import org.jetbrains.compose.resources.stringResource

enum class NemlaScreenAlignment { Top, Center }

@Composable
fun NemlaScreen(
    modifier: Modifier = Modifier,
    alignment: NemlaScreenAlignment = NemlaScreenAlignment.Top,
    maxContentWidth: Dp = NemlaTheme.sizes.contentMaxWidth,
    horizontalPadding: Dp = NemlaTheme.spacing.pageHorizontal,
    verticalPadding: Dp = NemlaTheme.spacing.pageVertical,
    topBar: (@Composable () -> Unit)? = null,
    scrollBehavior: NemlaTopBarScrollBehavior? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    NemlaScaffold(modifier = modifier, topBar = topBar ?: {}, scrollBehavior = scrollBehavior) { padding ->
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .edgeToEdgeBody(padding),
        ) {
            val viewportHeight = maxHeight
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = viewportHeight),
                contentAlignment = if (alignment == NemlaScreenAlignment.Center) Alignment.Center else Alignment.TopCenter,
            ) {
                Column(
                    modifier =
                        Modifier
                            .widthIn(max = maxContentWidth)
                            .fillMaxWidth()
                            .bottomSafeDrawingPadding()
                            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sectionGap),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun NemlaLoadingIndicator(modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.ds_loading)
    CircularProgressIndicator(
        modifier = modifier.semantics { contentDescription = description },
        color = NemlaTheme.colors.primary,
    )
}
