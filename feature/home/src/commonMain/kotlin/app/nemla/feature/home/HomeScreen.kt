package app.nemla.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.nemla.core.designsystem.brand.NemlaLogo
import app.nemla.core.designsystem.brand.NemlaLogoSize
import app.nemla.core.designsystem.component.NemlaBanner
import app.nemla.core.designsystem.component.NemlaBannerKind
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaCard
import app.nemla.core.designsystem.component.NemlaEmptyState
import app.nemla.core.designsystem.component.NemlaScreen
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.feature.home.resources.Res
import app.nemla.feature.home.resources.home_coming_body
import app.nemla.feature.home.resources.home_coming_title
import app.nemla.feature.home.resources.home_greeting
import app.nemla.feature.home.resources.home_greeting_anonymous
import app.nemla.feature.home.resources.home_sign_out
import app.nemla.feature.home.resources.home_sign_out_failed
import app.nemla.feature.home.resources.home_signing_out
import app.nemla.feature.home.resources.home_subtitle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun HomeRoute(
    onSignedOut: () -> Unit,
    floatingAction: @Composable () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnSignedOut by rememberUpdatedState(onSignedOut)
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    HomeEffect.SignedOut -> currentOnSignedOut()
                }
            }
        }
    }
    HomeScreen(state = state, onAction = viewModel::onAction, floatingAction = floatingAction)
}

@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
    floatingAction: @Composable () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        HomeContent(state = state, onAction = onAction)
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = NemlaTheme.spacing.lg, end = NemlaTheme.spacing.lg, bottom = NemlaTheme.spacing.lg),
        ) {
            floatingAction()
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
) {
    NemlaScreen(topBar = { HomeHeader(state = state, onAction = onAction) }) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs),
        ) {
            Text(
                text =
                    state.pseudo?.let {
                        stringResource(
                            Res.string.home_greeting,
                            it,
                        )
                    } ?: stringResource(Res.string.home_greeting_anonymous),
                style = NemlaTheme.typography.screenTitle,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.home_subtitle),
                style = NemlaTheme.typography.body,
                color = NemlaTheme.colors.mutedForeground,
            )
        }
        if (state.signOutFailed) {
            NemlaBanner(message = stringResource(Res.string.home_sign_out_failed), kind = NemlaBannerKind.Error)
        }
        NemlaCard(modifier = Modifier.fillMaxWidth()) {
            NemlaEmptyState(
                title = stringResource(Res.string.home_coming_title),
                message = stringResource(Res.string.home_coming_body),
                icon = NemlaIcons.Sparkles,
            )
        }
        Spacer(modifier = Modifier.height(FLOATING_ACTION_CLEARANCE))
    }
}

private val FLOATING_ACTION_CLEARANCE = 88.dp

@Composable
private fun HomeHeader(
    state: HomeUiState,
    onAction: (HomeAction) -> Unit,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NemlaTheme.spacing.pageHorizontal, vertical = NemlaTheme.spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NemlaLogo(size = NemlaLogoSize.Header)
            NemlaButton(
                text = stringResource(if (state.isSigningOut) Res.string.home_signing_out else Res.string.home_sign_out),
                onClick = { onAction(HomeAction.SignOutClicked) },
                variant = NemlaButtonVariant.Secondary,
                loading = state.isSigningOut,
                leadingIcon = NemlaIcons.LogOut,
            )
        }
        HorizontalDivider(color = NemlaTheme.colors.border)
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    NemlaTheme {
        HomeScreen(state = HomeUiState(), onAction = {})
    }
}
