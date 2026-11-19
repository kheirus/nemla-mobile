package app.nemla.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import app.nemla.core.designsystem.component.NemlaScaffold
import app.nemla.core.designsystem.component.NemlaScreen
import app.nemla.core.designsystem.component.NemlaScreenAlignment
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.PendingReview
import app.nemla.core.domain.model.ThemeMode
import app.nemla.core.domain.port.PendingReviewOpener
import app.nemla.core.network.BackendConfig
import app.nemla.feature.auth.navigation.AuthEntry
import app.nemla.feature.auth.navigation.authGraph
import app.nemla.feature.auth.navigation.isInAuthGraph
import app.nemla.feature.auth.navigation.navigateToAuth
import app.nemla.feature.dashboard.dashboardScreen
import app.nemla.feature.dashboard.isDashboard
import app.nemla.feature.dashboard.navigateToDashboard
import app.nemla.feature.onboarding.isOnboarding
import app.nemla.feature.onboarding.navigateToOnboarding
import app.nemla.feature.onboarding.onboardingScreen
import app.nemla.feature.reports.InboxBanners
import app.nemla.feature.reports.ReportArrivalNotice
import app.nemla.feature.reports.ReportHistory
import app.nemla.feature.settings.SettingsTab
import app.nemla.feature.settings.navigateToSettings
import app.nemla.feature.settings.settingsScreen
import app.nemla.feature.statementimport.StatementImportDialog
import app.nemla.feature.voice.capture.VoiceCaptureButton
import app.nemla.feature.voice.dictation.DictationTextButton
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
    val viewModel = koinViewModel<AppViewModel>()
    NotificationCoordinatorEffect()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val loaded = preferences
    if (loaded == null) {
        NemlaTheme { Box(modifier = Modifier.fillMaxSize().background(NemlaTheme.colors.background)) }
        return
    }
    val dark =
        when (loaded.theme) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    val navController = rememberNavController()
    ProvideAppLanguage(loaded.appLanguage) {
        NemlaTheme(darkTheme = dark) {
            val config = koinInject<BackendConfig>()
            if (config.isComplete) {
                NemlaNavHost(viewModel, navController)
            } else {
                MissingConfigurationScreen(isDebug = koinInject<AppBuildInfo>().isDebug)
            }
        }
    }
}

@Composable
private fun NemlaNavHost(
    viewModel: AppViewModel,
    navController: NavHostController,
) {
    val reviewOpener = koinInject<PendingReviewOpener>()
    val target by viewModel.target.collectAsStateWithLifecycle()
    LaunchedEffect(target) {
        when (val current = target) {
            null -> Unit
            RootTarget.Home -> navController.showHome()
            RootTarget.Onboarding -> navController.showOnboarding()
            is RootTarget.Auth -> navController.showAuth(current)
        }
    }
    val dictation: @Composable (Boolean, (String) -> Unit, (Boolean) -> Unit, (String) -> Unit) -> Unit =
        { enabled, onTranscript, onActiveChange, onMessage -> DictationTextButton(enabled, onTranscript, onActiveChange, onMessage) }
    NemlaScaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        NavHost(
            navController = navController,
            startDestination = SplashDestination,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            enterTransition = { appEnter() },
            exitTransition = { appExit() },
            popEnterTransition = { appPopEnter() },
            popExitTransition = { appPopExit() },
        ) {
            composable<SplashDestination> { SplashScreen() }
            authGraph(navController = navController, onAuthenticated = {})
            onboardingScreen(
                onFinished = { viewModel.onOnboardingFinished() },
                onSignedOut = { navController.showAuth(RootTarget.Auth(AuthEntry.SignIn)) },
                dictation = dictation,
            )
            dashboardScreen(
                onSignedOut = { navController.showAuth(RootTarget.Auth(AuthEntry.SignIn)) },
                onReviewPending = { review ->
                    reviewOpener.open(review)
                    navController.navigateToVoiceReview()
                },
                onOpenSettings = { categories ->
                    navController.navigateToSettings(if (categories) SettingsTab.CATEGORIES else SettingsTab.PROFILE)
                },
                floatingAction = { expanded ->
                    AddButtonWithImport(
                        expanded = expanded,
                        onOpenReview = { navController.navigateToVoiceReview() },
                        onImported = { review ->
                            reviewOpener.open(review)
                            navController.navigateToVoiceReview()
                        },
                    )
                },
                reportNotice = { ReportArrivalNotice() },
                notificationBanners = { InboxBanners() },
            )
            settingsScreen(
                navController = navController,
                onClose = { if (!navController.popBackStack()) navController.navigateToDashboard() },
                onSignedOut = { navController.showAuth(RootTarget.Auth(AuthEntry.SignIn)) },
                dictation = dictation,
                reportHistory = { ReportHistory() },
            )
            voiceGraph(onClose = { navController.closeVoiceReview() })
        }
    }
}

@Composable
private fun AddButtonWithImport(
    expanded: Boolean,
    onOpenReview: () -> Unit,
    onImported: (PendingReview) -> Unit,
) {
    var importOpen by rememberSaveable { mutableStateOf(false) }
    VoiceCaptureButton(onOpenReview = onOpenReview, onImportStatement = { importOpen = true }, expanded = expanded)
    if (importOpen) {
        StatementImportDialog(
            onClose = { importOpen = false },
            onImported = { review ->
                importOpen = false
                onImported(review)
            },
        )
    }
}

private fun NavHostController.showHome() {
    if (currentDestination?.isDashboard() == true) return
    navigateToDashboard()
}

private fun NavHostController.showOnboarding() {
    if (currentDestination?.isOnboarding() == true) return
    navigateToOnboarding()
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
