package app.nemla.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
    dismissible: Boolean = true,
    content: @Composable ColumnScope.(NemlaSheetController) -> Unit,
) {
    val colors = NemlaTheme.colors
    val currentDismissible by rememberUpdatedState(dismissible)
    val state =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = skipPartiallyExpanded,
            confirmValueChange = { value -> currentDismissible || value != SheetValue.Hidden },
        )
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val controller = remember(scope, state) { NemlaSheetController(scope, state) { currentOnDismiss() } }
    ModalBottomSheet(
        onDismissRequest = { if (currentDismissible) onDismissRequest() },
        modifier = modifier,
        sheetState = state,
        sheetGesturesEnabled = dismissible,
        containerColor = colors.popover,
        contentColor = colors.popoverForeground,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible, shouldDismissOnClickOutside = dismissible),
    ) {
        content(controller)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NemlaFormSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    dismissible: Boolean = true,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.(NemlaSheetController) -> Unit,
) {
    NemlaSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        skipPartiallyExpanded = skipPartiallyExpanded,
        dismissible = dismissible,
    ) { controller ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = NemlaTheme.spacing.lg)
                    .padding(bottom = NemlaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md),
        ) {
            NemlaSheetHeader(title = title, description = description)
            content(controller)
        }
    }
}

@Composable
fun NemlaSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs)) {
        Text(title, style = NemlaTheme.typography.sectionTitle, modifier = Modifier.semantics { heading() })
        if (description != null) {
            Text(description, style = NemlaTheme.typography.body, color = NemlaTheme.colors.mutedForeground)
        }
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
    NemlaListItem(
        headline = headline,
        modifier = modifier,
        supportingContent = supporting?.let { text -> { Text(text, style = NemlaTheme.typography.body) } },
        leadingContent = leadingIcon?.let { icon -> { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) } },
        contentColor = contentColor,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
fun NemlaListItem(
    headline: String,
    supportingContent: (@Composable () -> Unit)?,
    leadingContent: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
    contentColor: Color = NemlaTheme.colors.foreground,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = NemlaTheme.colors
    val clickModifier =
        if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier =
            modifier
                .then(clickModifier)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .fillMaxWidth()
                .heightIn(min = if (supportingContent != null) ListItemTwoLineMinHeight else ListItemOneLineMinHeight)
                .padding(horizontal = NemlaTheme.spacing.md, vertical = ListItemVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            CompositionLocalProvider(LocalContentColor provides contentColor) { leadingContent() }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(headline, style = NemlaTheme.typography.input, color = contentColor)
            if (supportingContent != null) {
                CompositionLocalProvider(
                    LocalContentColor provides colors.mutedForeground,
                    LocalTextStyle provides NemlaTheme.typography.caption,
                ) { supportingContent() }
            }
        }
        if (trailingContent != null) {
            CompositionLocalProvider(LocalContentColor provides colors.mutedForeground) { trailingContent() }
        }
    }
}

private val ListItemOneLineMinHeight = 56.dp
private val ListItemTwoLineMinHeight = 72.dp
private val ListItemVerticalPadding = 12.dp

@Composable
fun NemlaListIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = NemlaTheme.colors
    Box(
        modifier = modifier.size(ListIconContainer).clip(CircleShape).background(colors.primary.copy(alpha = LIST_ICON_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
    }
}

private val ListIconContainer = 44.dp
private const val LIST_ICON_ALPHA = 0.12f
