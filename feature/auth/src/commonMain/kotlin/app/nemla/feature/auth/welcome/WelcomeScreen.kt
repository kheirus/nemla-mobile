package app.nemla.feature.auth.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nemla.core.designsystem.brand.NemlaLogoMark
import app.nemla.core.designsystem.component.NemlaLightStatusBarIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.feature.auth.resources.Res
import app.nemla.feature.auth.resources.auth_welcome_brand
import app.nemla.feature.auth.resources.auth_welcome_have_account
import app.nemla.feature.auth.resources.auth_welcome_start
import app.nemla.feature.auth.resources.auth_welcome_subtitle
import app.nemla.feature.auth.resources.auth_welcome_title
import org.jetbrains.compose.resources.stringResource

private const val SUBTITLE_ALPHA = 0.82f
private val LogoSize = 104.dp
private val ButtonHeight = 54.dp

@Composable
internal fun WelcomeScreen(
    onStart: () -> Unit,
    onSignIn: () -> Unit,
) {
    NemlaLightStatusBarIcons()
    val colors = NemlaTheme.colors
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colors.primary)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = NemlaTheme.spacing.lg, vertical = NemlaTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.widthIn(max = NemlaTheme.sizes.contentMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md),
        ) {
            NemlaLogoMark(size = LogoSize, backgroundColor = colors.primaryForeground)
            Text(
                text = stringResource(Res.string.auth_welcome_brand),
                style = NemlaTheme.typography.brandAuth.copy(fontSize = 44.sp, lineHeight = 48.sp),
                color = colors.primaryForeground,
            )
            Text(
                text = stringResource(Res.string.auth_welcome_title),
                style = NemlaTheme.typography.screenTitle,
                color = colors.primaryForeground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = NemlaTheme.spacing.lg).semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.auth_welcome_subtitle),
                style = NemlaTheme.typography.body,
                color = colors.primaryForeground.copy(alpha = SUBTITLE_ALPHA),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1.3f))
        Column(
            modifier = Modifier.widthIn(max = NemlaTheme.sizes.contentMaxWidth).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
        ) {
            Button(
                onClick = onStart,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryForeground, contentColor = colors.primary),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight),
            ) {
                Text(stringResource(Res.string.auth_welcome_start), style = NemlaTheme.typography.button)
            }
            TextButton(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().heightIn(min = NemlaTheme.sizes.minTouchTarget),
            ) {
                Text(
                    stringResource(Res.string.auth_welcome_have_account),
                    style = NemlaTheme.typography.button,
                    color = colors.primaryForeground,
                )
            }
        }
    }
}

@Preview
@Composable
private fun WelcomeScreenPreview() {
    NemlaTheme { WelcomeScreen(onStart = {}, onSignIn = {}) }
}
