package app.nemla.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.nemla.core.common.AppBuildInfo
import app.nemla.core.designsystem.brand.NemlaLogo
import app.nemla.core.designsystem.component.NemlaCard
import app.nemla.core.designsystem.component.NemlaLoadingIndicator
import app.nemla.core.designsystem.component.NemlaScreen
import app.nemla.core.designsystem.component.NemlaScreenAlignment
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.network.BackendConfig
import app.nemla.feature.auth.navigation.AuthEntry
import app.nemla.feature.auth.navigation.authGraph
import app.nemla.feature.auth.navigation.isInAuthGraph
import app.nemla.feature.auth.navigation.navigateToAuth
import app.nemla.feature.home.homeScreen
import app.nemla.feature.home.isHome
import app.nemla.feature.home.navigateToHome
import app.nemla.feature.voice.capture.VoiceCaptureButton
import app.nemla.feature.voice.navigation.closeVoiceReview
import app.nemla.feature.voice.navigation.navigateToVoiceReview
import app.nemla.feature.voice.navigation.voiceGraph
import app.nemla.shared.resources.Res
import app.nemla.shared.resources.app_missing_config_body
import app.nemla.shared.resources.app_missing_config_title
import app.nemla.shared.resources.app_unavailable_body
import app.nemla.shared.resources.app_unavailable_title
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Serializable
internal data object SplashDestination

@Composable
fun App() {
    NemlaTheme {
        val config = koinInject<BackendConfig>()
        if (config.isComplete) {
            NemlaNavHost()
        } else {
            MissingConfigurationScreen(isDebug = koinInject<AppBuildInfo>().isDebug)
        }
    }
}

@Composable
private fun NemlaNavHost(viewModel: AppViewModel = koinViewModel()) {
    val navController = rememberNavController()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    var hadSession by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(authState) {
        when (val target = rootTargetFor(authState, hadSession)) {
            null -> {
                Unit
            }

            RootTarget.Home -> {
                hadSession = true
                navController.showHome()
            }

            is RootTarget.Auth -> {
                navController.showAuth(target)
            }
        }
    }
    NavHost(navController = navController, startDestination = SplashDestination) {
        composable<SplashDestination> { SplashScreen() }
        authGraph(navController = navController, onAuthenticated = { navController.showHome() })
        homeScreen(
            onSignedOut = { navController.showAuth(RootTarget.Auth(AuthEntry.SignIn)) },
            floatingAction = { VoiceCaptureButton(onOpenReview = { navController.navigateToVoiceReview() }) },
        )
        voiceGraph(onClose = { navController.closeVoiceReview() })
    }
}

private fun NavHostController.showHome() {
    if (currentDestination?.isHome() == true) return
    navigateToHome()
}

private fun NavHostController.showAuth(target: RootTarget.Auth) {
    if (currentDestination?.isInAuthGraph() == true) return
    navigateToAuth(target.entry)
}

@Composable
private fun SplashScreen() {
    NemlaScreen(alignment = NemlaScreenAlignment.Center) {
        NemlaLogo()
        NemlaLoadingIndicator()
    }
}

@Composable
private fun MissingConfigurationScreen(isDebug: Boolean) {
    NemlaScreen(alignment = NemlaScreenAlignment.Center) {
        NemlaLogo()
        NemlaCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(if (isDebug) Res.string.app_missing_config_title else Res.string.app_unavailable_title),
                    style = NemlaTheme.typography.screenTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(if (isDebug) Res.string.app_missing_config_body else Res.string.app_unavailable_body),
                    style = NemlaTheme.typography.body,
                    color = NemlaTheme.colors.mutedForeground,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
