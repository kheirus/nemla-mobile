package app.nemla.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme

private const val AVATAR_ALPHA = 0.16f
private const val ACCOUNT_RING_ALPHA = 0.45f
private const val ACCOUNT_ICON_RATIO = 0.55f
private const val MAX_INITIALS = 2
private val ButtonAvatarSize = 34.dp
private val SheetAvatarSize = 48.dp

fun nemlaInitials(name: String?): String? {
    val words = name?.split(' ', '-', '_', '.', '\t', '\n')?.filter { it.isNotBlank() }.orEmpty()
    if (words.isEmpty()) return null
    val letters = if (words.size == 1) words.first().take(1) else words.take(MAX_INITIALS).joinToString("") { it.take(1) }
    return letters.uppercase()
}

@Composable
fun NemlaAvatar(
    name: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector = NemlaIcons.UserRound,
) {
    val colors = NemlaTheme.colors
    val initials = nemlaInitials(name)
    Box(
        modifier =
            modifier
                .size(size)
                .background(colors.primary.copy(alpha = AVATAR_ALPHA).compositeOver(colors.background), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (initials == null) {
            Icon(fallbackIcon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(size / 2))
        } else {
            Text(
                text = initials,
                style = NemlaTheme.typography.bodyEmphasis.copy(fontSize = (size.value * 0.4f).sp, lineHeight = (size.value * 0.4f).sp),
                color = colors.primary,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun NemlaAccountButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = NemlaIcons.UserRound,
) {
    val colors = NemlaTheme.colors
    IconButton(onClick = onClick, modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        Box(
            modifier =
                Modifier
                    .size(ButtonAvatarSize)
                    .background(colors.primary.copy(alpha = AVATAR_ALPHA).compositeOver(colors.background), CircleShape)
                    .border(1.5.dp, colors.primary.copy(alpha = ACCOUNT_RING_ALPHA), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(ButtonAvatarSize * ACCOUNT_ICON_RATIO))
        }
    }
}

@Composable
fun NemlaAccountSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    avatarName: String? = title,
    fallbackIcon: ImageVector = NemlaIcons.UserRound,
    content: @Composable ColumnScope.(NemlaSheetController) -> Unit,
) {
    NemlaSheet(onDismissRequest = onDismissRequest, modifier = modifier) { controller ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NemlaTheme.spacing.md, vertical = NemlaTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NemlaAvatar(name = avatarName, size = SheetAvatarSize, fallbackIcon = fallbackIcon)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = NemlaTheme.typography.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail != null) {
                    Text(
                        detail,
                        style = NemlaTheme.typography.body,
                        color = NemlaTheme.colors.mutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HorizontalDivider(
            color = NemlaTheme.colors.border,
            modifier = Modifier.padding(top = NemlaTheme.spacing.sm, bottom = NemlaTheme.spacing.xxs),
        )
        content(controller)
        Spacer(modifier = Modifier.height(NemlaTheme.spacing.md))
    }
}
