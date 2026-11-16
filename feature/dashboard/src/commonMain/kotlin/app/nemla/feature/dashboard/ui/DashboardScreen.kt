package app.nemla.feature.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import app.nemla.core.designsystem.component.NemlaLoadingIndicator
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.PendingReview
import app.nemla.core.domain.model.PeriodCursor
import app.nemla.feature.dashboard.DashboardAction
import app.nemla.feature.dashboard.DashboardEffect
import app.nemla.feature.dashboard.DashboardError
import app.nemla.feature.dashboard.DashboardNotice
import app.nemla.feature.dashboard.DashboardUiState
import app.nemla.feature.dashboard.DashboardViewModel
import app.nemla.feature.dashboard.logic.DashboardCalculator
import app.nemla.feature.dashboard.logic.DashboardInputs
import app.nemla.feature.dashboard.resources.Res
import app.nemla.feature.dashboard.resources.dashboard_account_menu
import app.nemla.feature.dashboard.resources.dashboard_error_generic
import app.nemla.feature.dashboard.resources.dashboard_error_offline
import app.nemla.feature.dashboard.resources.dashboard_error_timeout
import app.nemla.feature.dashboard.resources.dashboard_loading
import app.nemla.feature.dashboard.resources.dashboard_my_space
import app.nemla.feature.dashboard.resources.dashboard_notice_deleted
import app.nemla.feature.dashboard.resources.dashboard_notice_updated
import app.nemla.feature.dashboard.resources.dashboard_retry
import app.nemla.feature.dashboard.resources.dashboard_sign_out
import app.nemla.feature.dashboard.resources.dashboard_sign_out_failed
import app.nemla.feature.dashboard.resources.dashboard_signing_out
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val FloatingActionClearance = 88.dp
private const val NOTICE_DURATION_MS = 4_000L
private const val AVATAR_ALPHA = 0.15f

@Composable
internal fun DashboardRoute(
    onSignedOut: () -> Unit,
    onReviewPending: (PendingReview) -> Unit,
    floatingAction: @Composable () -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnSignedOut by rememberUpdatedState(onSignedOut)
    val currentOnReviewPending by rememberUpdatedState(onReviewPending)
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onAction(DashboardAction.Started)
            viewModel.effects.collect { effect ->
                when (effect) {
                    is DashboardEffect.OpenReview -> currentOnReviewPending(effect.review)
                    DashboardEffect.SignedOut -> currentOnSignedOut()
                }
            }
        }
    }
    DashboardScreen(state = state, onAction = viewModel::onAction, floatingAction = floatingAction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardScreen(
    state: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
    floatingAction: @Composable () -> Unit = {},
) {
    val colors = NemlaTheme.colors
    val format = remember(state.language, state.currency) { DashboardFormat(state.language, state.currency) }
    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            DashboardHeader(state, onAction)
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { onAction(DashboardAction.PullToRefresh) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .widthIn(max = NemlaTheme.sizes.contentMaxWidth)
                                .fillMaxWidth()
                                .padding(horizontal = NemlaTheme.spacing.pageHorizontal, vertical = NemlaTheme.spacing.pageVertical),
                        verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sectionGap),
                    ) {
                        DashboardContent(state, format, onAction)
                        Spacer(modifier = Modifier.height(FloatingActionClearance))
                    }
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = NemlaTheme.spacing.lg, end = NemlaTheme.spacing.lg, bottom = NemlaTheme.spacing.lg),
        ) {
            floatingAction()
        }
        state.editor?.let { ExpenseEditorDialog(it, state, format, onAction) }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    format: DashboardFormat,
    onAction: (DashboardAction) -> Unit,
) {
    state.notice?.let { notice ->
        LaunchedEffect(notice) {
            delay(NOTICE_DURATION_MS)
            onAction(DashboardAction.NoticeDismissed)
        }
        NemlaBanner(
            message = stringResource(notice.resource()),
            kind = NemlaBannerKind.Success,
            modifier = Modifier.clickable { onAction(DashboardAction.NoticeDismissed) },
        )
    }
    state.loadError?.let { error ->
        Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
            NemlaBanner(message = stringResource(error.resource()), kind = NemlaBannerKind.Error)
            NemlaButton(
                text = stringResource(Res.string.dashboard_retry),
                onClick = { onAction(DashboardAction.RetryClicked) },
                variant = NemlaButtonVariant.Outline,
                leadingIcon = NemlaIcons.RefreshCw,
            )
        }
    }
    if (state.signOutFailed) {
        NemlaBanner(message = stringResource(Res.string.dashboard_sign_out_failed), kind = NemlaBannerKind.Error)
    }
    if (state.isInitialLoading) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = NemlaTheme.spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NemlaLoadingIndicator(modifier = Modifier.size(NemlaTheme.sizes.iconLarge))
            MutedCaption(stringResource(Res.string.dashboard_loading))
        }
        return
    }
    if (state.view.pending.isNotEmpty()) PendingCard(state.view.pending, format, onAction)
    if (state.view.alerts.isNotEmpty()) AlertsCard(state.view.alerts, format)
    SearchCard(state, format, onAction)
    PilotCard(state, format, onAction)
    SpendingCard(state, format, onAction)
    TodayCard(state, format, onAction)
}

@Composable
private fun DashboardHeader(
    state: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
) {
    val colors = NemlaTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    val title = state.pseudo ?: stringResource(Res.string.dashboard_my_space)
    val menuDescription = stringResource(Res.string.dashboard_account_menu)
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
            Box {
                Surface(
                    shape = CircleShape,
                    color = colors.secondary,
                    contentColor = colors.secondaryForeground,
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { menuOpen = true }
                            .semantics { contentDescription = menuDescription },
                ) {
                    Row(
                        modifier = Modifier.padding(start = 4.dp, end = NemlaTheme.spacing.sm, top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(28.dp)
                                    .background(colors.accent.copy(alpha = AVATAR_ALPHA).compositeOver(colors.secondary), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(NemlaIcons.UserRound, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = title,
                            style = NemlaTheme.typography.bodyStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 96.dp),
                        )
                        Icon(
                            NemlaIcons.ChevronDown,
                            contentDescription = null,
                            tint = colors.secondaryForeground.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(NemlaTheme.radius.md),
                    containerColor = colors.popover,
                ) {
                    Text(
                        text = title,
                        style = NemlaTheme.typography.bodyStrong,
                        modifier = Modifier.padding(horizontal = NemlaTheme.spacing.md, vertical = NemlaTheme.spacing.xs),
                    )
                    HorizontalDivider(color = colors.border)
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(if (state.isSigningOut) Res.string.dashboard_signing_out else Res.string.dashboard_sign_out),
                                style = NemlaTheme.typography.body,
                            )
                        },
                        leadingIcon = { Icon(NemlaIcons.LogOut, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        enabled = !state.isSigningOut,
                        onClick = {
                            menuOpen = false
                            onAction(DashboardAction.SignOutClicked)
                        },
                    )
                }
            }
        }
        HorizontalDivider(color = colors.border)
    }
}

private fun DashboardNotice.resource(): StringResource =
    when (this) {
        DashboardNotice.EXPENSE_UPDATED -> Res.string.dashboard_notice_updated
        DashboardNotice.EXPENSE_DELETED -> Res.string.dashboard_notice_deleted
    }

private fun DashboardError.resource(): StringResource =
    when (this) {
        DashboardError.OFFLINE -> Res.string.dashboard_error_offline
        DashboardError.TIMEOUT -> Res.string.dashboard_error_timeout
        DashboardError.GENERIC -> Res.string.dashboard_error_generic
    }

@Preview
@Composable
private fun DashboardScreenPreview() {
    val today = LocalDate(2026, 11, 16)
    val cursor = PeriodCursor.monthOf(today)
    NemlaTheme {
        DashboardScreen(
            state =
                DashboardUiState(
                    language = AppLanguage.FR,
                    today = today,
                    cursor = cursor,
                    view = DashboardCalculator.compute(DashboardInputs(today = today, cursor = cursor)),
                    isInitialLoading = false,
                ),
            onAction = {},
        )
    }
}
