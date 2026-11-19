package app.nemla.core.designsystem.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.resources.Res
import app.nemla.core.designsystem.resources.ds_brand_name
import app.nemla.core.designsystem.resources.nemla_logo
import app.nemla.core.designsystem.theme.NemlaTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class NemlaLogoSize { Auth, Header }

@Composable
fun NemlaLogo(
    modifier: Modifier = Modifier,
    size: NemlaLogoSize = NemlaLogoSize.Auth,
) {
    val name = stringResource(Res.string.ds_brand_name)
    val markSize = if (size == NemlaLogoSize.Auth) NemlaTheme.sizes.logoAuth else NemlaTheme.sizes.logoHeader
    val textStyle = if (size == NemlaLogoSize.Auth) NemlaTheme.typography.brandAuth else NemlaTheme.typography.brandHeader
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = name },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NemlaLogoMark(markSize)
        Text(
            text = name,
            style = textStyle,
            color = NemlaTheme.colors.primary,
            modifier = Modifier.offset(y = 4.dp),
        )
    }
}

@Composable
fun NemlaLogoMark(
    size: Dp,
    backgroundColor: Color? = null,
) {
    if (backgroundColor == null) {
        Image(painter = painterResource(Res.drawable.nemla_logo), contentDescription = null, modifier = Modifier.size(size))
        return
    }
    Box(
        modifier = Modifier.size(size).background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.nemla_logo),
            contentDescription = null,
            modifier = Modifier.size(size * LOGO_IN_CIRCLE),
        )
    }
}

private const val LOGO_IN_CIRCLE = 0.66f
