package app.nemla.feature.auth.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.nemla.core.designsystem.brand.NemlaLogo
import app.nemla.core.designsystem.component.NemlaBanner
import app.nemla.core.designsystem.component.NemlaCard
import app.nemla.core.designsystem.component.NemlaLinkTone
import app.nemla.core.designsystem.component.NemlaScreen
import app.nemla.core.designsystem.component.NemlaScreenAlignment
import app.nemla.core.designsystem.component.NemlaTextButton
import app.nemla.core.designsystem.theme.NemlaTheme
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AuthLayout(
    title: String,
    subtitle: String,
    footerLink: String,
    onFooterLink: () -> Unit,
    footerEnabled: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    NemlaScreen(
        alignment = NemlaScreenAlignment.Center,
        verticalPadding = NemlaTheme.spacing.xxxl,
    ) {
        Column(
            modifier = Modifier.widthIn(max = NemlaTheme.sizes.authCardMaxWidth).fillMaxWidth(),
        ) {
            NemlaLogo(modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(NemlaTheme.spacing.xxl))
            NemlaCard(modifier = Modifier.fillMaxWidth(), contentPadding = NemlaTheme.spacing.xl) {
                Text(
                    text = title,
                    style = NemlaTheme.typography.screenTitle,
                    color = NemlaTheme.colors.cardForeground,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = subtitle,
                    style = NemlaTheme.typography.body,
                    color = NemlaTheme.colors.mutedForeground,
                    modifier = Modifier.padding(top = NemlaTheme.spacing.xxs),
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = NemlaTheme.spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.fieldGap),
                    content = content,
                )
                NemlaTextButton(
                    text = footerLink,
                    onClick = onFooterLink,
                    tone = NemlaLinkTone.Muted,
                    enabled = footerEnabled,
                    modifier = Modifier.fillMaxWidth().padding(top = NemlaTheme.spacing.sm),
                )
            }
        }
    }
}

@Composable
internal fun AuthBannerView(banner: AuthBanner?) {
    if (banner == null) return
    NemlaBanner(message = stringResource(banner.message.resource), kind = banner.kind.designKind)
}

@Composable
internal fun <T> CollectEffects(
    effects: Flow<T>,
    onEffect: (T) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { currentOnEffect(it) }
        }
    }
}
