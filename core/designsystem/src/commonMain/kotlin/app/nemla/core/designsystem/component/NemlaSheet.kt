package app.nemla.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.theme.NemlaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val DISABLED_ALPHA = 0.5f

@OptIn(ExperimentalMaterial3Api::class)
@Stable
class NemlaSheetController internal constructor(
    private val scope: CoroutineScope,
    private val state: SheetState,
    private val onHidden: () -> Unit,
) {
    fun dismiss(then: () -> Unit = {}) {
        scope.launch { state.hide() }.invokeOnCompletion {
            onHidden()
            then()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NemlaSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.(NemlaSheetController) -> Unit,
) {
    val colors = NemlaTheme.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val controller = remember(scope, state) { NemlaSheetController(scope, state) { currentOnDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = state,
        containerColor = colors.popover,
        contentColor = colors.popoverForeground,
    ) {
        content(controller)
    }
}

@Composable
fun NemlaListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    contentColor: Color = NemlaTheme.colors.foreground,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = NemlaTheme.colors
    val clickModifier =
        if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier
    ListItem(
        headlineContent = { Text(headline, style = NemlaTheme.typography.input) },
        modifier = modifier.then(clickModifier).alpha(if (enabled) 1f else DISABLED_ALPHA),
        supportingContent = supporting?.let { text -> { Text(text, style = NemlaTheme.typography.body) } },
        leadingContent = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) } },
        colors =
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = contentColor,
                supportingColor = colors.mutedForeground,
                leadingIconColor = contentColor,
            ),
    )
}
