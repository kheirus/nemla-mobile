package app.nemla.feature.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.nemla.core.designsystem.component.NemlaAccountButton
import app.nemla.core.designsystem.component.NemlaAccountSheet
import app.nemla.core.designsystem.component.NemlaBanner
import app.nemla.core.designsystem.component.NemlaBannerKind
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaListItem
import app.nemla.core.designsystem.component.NemlaLoadingIndicator
import app.nemla.core.designsystem.component.NemlaPullToRefreshBox
import app.nemla.core.designsystem.component.NemlaRotationAllowed
import app.nemla.core.designsystem.component.NemlaScaffold
import app.nemla.core.designsystem.component.NemlaSnackbarEffect
import app.nemla.core.designsystem.component.NemlaTopBar
import app.nemla.core.designsystem.component.NemlaTopBarBrand
import app.nemla.core.designsystem.component.NemlaTopBarScrollBehavior
import app.nemla.core.designsystem.component.bottomSafeDrawingPadding
import app.nemla.core.designsystem.component.edgeToEdgeBody
import app.nemla.core.designsystem.component.rememberNemlaTopBarScrollBehavior
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.AppLanguage
import app.nemla.core.domain.model.Household
import app.nemla.core.domain.model.PendingReview
import app.nemla.core.domain.model.PeriodCursor
import app.nemla.core.domain.model.PeriodKind
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
import app.nemla.feature.dashboard.resources.dashboard_notice_deleted
import app.nemla.feature.dashboard.resources.dashboard_notice_updated
import app.nemla.feature.dashboard.resources.dashboard_retry
import app.nemla.feature.dashboard.resources.dashboard_sign_out_failed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.seconds

private val FloatingActionClearance = 72.dp
private val WideLayoutMinWidth = 840.dp
private val CompactHeight = 480.dp
private val WideContentMaxWidth = 1280.dp
private val FloatingActionLabelDuration = 2.5.seconds

@Composable
internal fun DashboardRoute(
    onSignedOut: () -> Unit,
    onReviewPending: (PendingReview) -> Unit,
    onOpenSettings: (Boolean) -> Unit,
    floatingAction: @Composable (expanded: Boolean) -> Unit = {},
    reportNotice: @Composable () -> Unit = {},
    notificationBanners: @Composable () -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnSignedOut by rememberUpdatedState(onSignedOut)
    val currentOnReviewPending by rememberUpdatedState(onReviewPending)
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onAction(DashboardAction.Started)
            viewModel.effects.collect { effect ->
                when (effect) {
                    is DashboardEffect.OpenReview -> currentOnReviewPending(effect.review)
                    DashboardEffect.SignedOut -> currentOnSignedOut()
                    is DashboardEffect.OpenSettings -> currentOnOpenSettings(effect.categories)
                }
            }
        }
    }
    DashboardScreen(
        state = state,
        onAction = viewModel::onAction,
        floatingAction = floatingAction,
        reportNotice = reportNotice,
        notificationBanners = notificationBanners,
    )
}

@Composable
internal fun DashboardScreen(
    state: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
    floatingAction: @Composable (expanded: Boolean) -> Unit = {},
    reportNotice: @Composable () -> Unit = {},
    notificationBanners: @Composable () -> Unit = {},
) {
    val format = remember(state.language, state.currency) { DashboardFormat(state.language, state.currency) }
    val scrollBehavior = rememberNemlaTopBarScrollBehavior()
    val scrollState = rememberScrollState()
    var fabExpanded by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(scrollState) {
        if (fabExpanded) {
            withTimeoutOrNull(FloatingActionLabelDuration) { snapshotFlow { scrollState.value }.first { it > 0 } }
            fabExpanded = false
        }
    }
    NemlaRotationAllowed()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideLayoutMinWidth
        NemlaScaffold(
            topBar = { DashboardTopBar(state, onAction, scrollBehavior) },
            floatingActionButton = { floatingAction(fabExpanded) },
            scrollBehavior = scrollBehavior,
        ) { padding ->
            NemlaPullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { onAction(DashboardAction.PullToRefresh) },
                modifier = Modifier.fillMaxSize().edgeToEdgeBody(padding),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .widthIn(max = if (wide) WideContentMaxWidth else NemlaTheme.sizes.contentMaxWidth)
                                .fillMaxWidth()
                                .bottomSafeDrawingPadding()
                                .padding(
                                    start = NemlaTheme.spacing.pageHorizontal,
                                    top = NemlaTheme.spacing.pageVertical,
                                    end = NemlaTheme.spacing.pageHorizontal,
                                    bottom = FloatingActionClearance,
                                ),
                        verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sectionGap),
                    ) {
                        DashboardContent(state, format, onAction, reportNotice, wide, notificationBanners)
                    }
                }
            }
            state.editor?.let { ExpenseEditorSheet(it, state, format, onAction) }
        }
        if (maxWidth > maxHeight && maxHeight < CompactHeight && !state.isInitialLoading) LandscapeChart(state, format, onAction)
    }
}

@Composable
private fun LandscapeChart(
    state: DashboardUiState,
    format: DashboardFormat,
    onAction: (DashboardAction) -> Unit,
) {
    val cursor = state.cursor
    val period = if (cursor.kind == PeriodKind.YEAR) cursor.year.toString() else format.monthTitle(cursor.month)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(NemlaTheme.colors.background)
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = NemlaTheme.spacing.pageHorizontal, vertical = NemlaTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm)) {
            Text(
                text = period,
                style = NemlaTheme.typography.bodyEmphasis,
                maxLines = 1,
            )
            Text(
                text = format.money(state.view.pilot.spentCents),
                style = NemlaTheme.typography.bodyEmphasis.tabular(),
                color = NemlaTheme.colors.primary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            FilterButton(state.view.pilot.filter, onAction)
        }
        MonthlyChartSection(
            chart = state.view.pilot.chart,
            animationKey = cursor.kind,
            format = format,
            onMonthTapped = { onAction(DashboardAction.ChartMonthTapped(it)) },
            modifier = Modifier.weight(1f),
            fillHeight = true,
            showHint = false,
        )
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    format: DashboardFormat,
    onAction: (DashboardAction) -> Unit,
    reportNotice: @Composable () -> Unit,
    wide: Boolean,
    notificationBanners: @Composable () -> Unit,
) {
    NemlaSnackbarEffect(
        message = state.notice?.let { stringResource(it.resource()) },
        onShown = { onAction(DashboardAction.NoticeDismissed) },
    )
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
    if (state.view.alerts.isNotEmpty()) AlertsCard(state.view.alerts, format) { onAction(DashboardAction.AdjustBudgetsClicked) }
    reportNotice()
    notificationBanners()
    SearchCard(state, format, onAction)
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sectionGap), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sectionGap)) {
                PilotCard(state, format, onAction)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sectionGap)) {
                SpendingCard(state, format, onAction)
                TodayCard(state, format, onAction)
            }
        }
    } else {
        PilotCard(state, format, onAction)
        SpendingCard(state, format, onAction)
        TodayCard(state, format, onAction)
    }
}

@Composable
private fun DashboardTopBar(
    state: DashboardUiState,
    onAction: (DashboardAction) -> Unit,
    scrollBehavior: NemlaTopBarScrollBehavior,
) {
    NemlaTopBar(
        title = { NemlaTopBarBrand() },
        actions = {
            NemlaAccountButton(
                contentDescription = stringResource(Res.string.dashboard_account_menu),
                onClick = { onAction(DashboardAction.ManageAccountClicked) },
                icon = householdIcon(state.household),
            )
        },
        scrollBehavior = scrollBehavior,
    )
}

internal fun householdIcon(household: Household?): ImageVector =
    when (household) {
        Household.COUPLE -> NemlaIcons.HeartHandshake
        Household.FAMILY -> NemlaIcons.Baby
        Household.FLATSHARE -> NemlaIcons.HousePlus
        Household.SINGLE, null -> NemlaIcons.UserRound
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
