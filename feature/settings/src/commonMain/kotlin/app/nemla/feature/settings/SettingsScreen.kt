package app.nemla.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.nemla.core.designsystem.component.NemlaAlertDialog
import app.nemla.core.designsystem.component.NemlaBackButton
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaCard
import app.nemla.core.designsystem.component.NemlaListIcon
import app.nemla.core.designsystem.component.NemlaListItem
import app.nemla.core.designsystem.component.NemlaOption
import app.nemla.core.designsystem.component.NemlaScaffold
import app.nemla.core.designsystem.component.NemlaScreen
import app.nemla.core.designsystem.component.NemlaSnackbarEffect
import app.nemla.core.designsystem.component.NemlaTabs
import app.nemla.core.designsystem.component.NemlaTopBar
import app.nemla.core.designsystem.component.NemlaTopBarScrollBehavior
import app.nemla.core.designsystem.component.NemlaTopBarTitle
import app.nemla.core.designsystem.component.bottomSafeDrawingPadding
import app.nemla.core.designsystem.component.edgeToEdgeBody
import app.nemla.core.designsystem.component.rememberNemlaTopBarScrollBehavior
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.Household
import app.nemla.feature.settings.categories.CategoriesTabRoute
import app.nemla.feature.settings.common.money
import app.nemla.feature.settings.credits.CreditsTabRoute
import app.nemla.feature.settings.resources.Res
import app.nemla.feature.settings.resources.settings_cancel
import app.nemla.feature.settings.resources.settings_danger_body
import app.nemla.feature.settings.resources.settings_danger_title
import app.nemla.feature.settings.resources.settings_delete
import app.nemla.feature.settings.resources.settings_delete_confirm
import app.nemla.feature.settings.resources.settings_delete_confirm_title
import app.nemla.feature.settings.resources.settings_delete_failed
import app.nemla.feature.settings.resources.settings_delete_unavailable
import app.nemla.feature.settings.resources.settings_entry_budget
import app.nemla.feature.settings.resources.settings_entry_budget_amount
import app.nemla.feature.settings.resources.settings_entry_budget_help
import app.nemla.feature.settings.resources.settings_entry_card
import app.nemla.feature.settings.resources.settings_entry_card_help
import app.nemla.feature.settings.resources.settings_entry_preferences
import app.nemla.feature.settings.resources.settings_entry_preferences_help
import app.nemla.feature.settings.resources.settings_entry_profile
import app.nemla.feature.settings.resources.settings_entry_profile_help
import app.nemla.feature.settings.resources.settings_entry_recurring
import app.nemla.feature.settings.resources.settings_entry_recurring_help
import app.nemla.feature.settings.resources.settings_sign_out
import app.nemla.feature.settings.resources.settings_sign_out_failed
import app.nemla.feature.settings.resources.settings_tab_categories
import app.nemla.feature.settings.resources.settings_tab_credits
import app.nemla.feature.settings.resources.settings_tab_profile
import app.nemla.feature.settings.resources.settings_tab_reports
import app.nemla.feature.settings.resources.settings_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun SettingsRoute(
    initialTab: SettingsTab,
    requestedTab: SettingsTab?,
    onRequestedTabShown: () -> Unit,
    onOpenPanel: (SettingsPanel) -> Unit,
    onClose: () -> Unit,
    onSignedOut: () -> Unit,
    reportHistory: @Composable () -> Unit,
    viewModel: SettingsViewModel = koinViewModel { parametersOf(initialTab) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnSignedOut by rememberUpdatedState(onSignedOut)
    val currentOnRequestedTabShown by rememberUpdatedState(onRequestedTabShown)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    SettingsEffect.Close -> currentOnClose()
                    SettingsEffect.SignedOut -> currentOnSignedOut()
                }
            }
        }
    }
    var resumedOnce by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumedOnce) viewModel.onAction(SettingsAction.Started) else resumedOnce = true
    }
    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            viewModel.onAction(SettingsAction.TabSelected(requestedTab))
            currentOnRequestedTabShown()
        }
    }
    SettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenPanel = onOpenPanel,
        reportHistory = reportHistory,
    )
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onOpenPanel: (SettingsPanel) -> Unit,
    reportHistory: @Composable () -> Unit,
) {
    val scrollBehavior = rememberNemlaTopBarScrollBehavior()
    val tabs = settingsTabs()
    val pagerState = rememberPagerState(initialPage = tabs.indexOfTab(state.tab).coerceAtLeast(0)) { tabs.size }
    val scope = rememberCoroutineScope()
    val currentState by rememberUpdatedState(state)
    LaunchedEffect(pagerState, tabs) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val tab = tabs.getOrNull(page)?.value
            if (tab != null && tab != currentState.tab) onAction(SettingsAction.TabSelected(tab))
        }
    }
    LaunchedEffect(state.tab) {
        val target = tabs.indexOfTab(state.tab)
        if (target >= 0 && target != pagerState.currentPage && !pagerState.isScrollInProgress) pagerState.animateScrollToPage(target)
    }
    NemlaScaffold(topBar = { SettingsTopBar(onAction, scrollBehavior) }, scrollBehavior = scrollBehavior) { padding ->
        NemlaSnackbarEffect(
            message = state.accountMessage?.let { stringResource(it.resource()) },
            onShown = { onAction(SettingsAction.AccountMessageDismissed) },
        )
        Column(modifier = Modifier.fillMaxSize().edgeToEdgeBody(padding)) {
            NemlaTabs(
                options = tabs,
                selected = tabs.getOrNull(pagerState.currentPage)?.value ?: state.tab,
                onSelect = { tab ->
                    val index = tabs.indexOfTab(tab)
                    if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) }
                },
                indicatorPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.Top,
                key = { page -> tabs[page].value },
            ) { page ->
                SettingsPage {
                    when (tabs[page].value) {
                        SettingsTab.PROFILE -> EntryList(state, onAction, onOpenPanel)
                        SettingsTab.REPORTS -> reportHistory()
                        SettingsTab.CREDITS -> CreditsTabRoute()
                        SettingsTab.CATEGORIES -> CategoriesTabRoute()
                    }
                }
            }
        }
    }
    if (state.isConfirmingDelete) DeleteDialog(onAction)
}

@Composable
private fun SettingsPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = NemlaTheme.sizes.contentMaxWidth)
                    .fillMaxWidth()
                    .bottomSafeDrawingPadding()
                    .padding(horizontal = NemlaTheme.spacing.pageHorizontal, vertical = NemlaTheme.spacing.pageVertical),
            verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md),
            content = content,
        )
    }
}

@Composable
internal fun SettingsPanelScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scrollBehavior = rememberNemlaTopBarScrollBehavior()
    NemlaScreen(
        topBar = {
            NemlaTopBar(
                title = { NemlaTopBarTitle(title) },
                navigationIcon = { NemlaBackButton(onClick = onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
        scrollBehavior = scrollBehavior,
    ) {
        content()
    }
}

private fun List<NemlaOption<SettingsTab>>.indexOfTab(tab: SettingsTab): Int = indexOfFirst { it.value == tab }

@Composable
private fun settingsTabs(): List<NemlaOption<SettingsTab>> =
    listOf(
        NemlaOption(SettingsTab.PROFILE, stringResource(Res.string.settings_tab_profile)),
        NemlaOption(SettingsTab.REPORTS, stringResource(Res.string.settings_tab_reports)),
        NemlaOption(SettingsTab.CREDITS, stringResource(Res.string.settings_tab_credits)),
        NemlaOption(SettingsTab.CATEGORIES, stringResource(Res.string.settings_tab_categories)),
    )

@Composable
private fun SettingsTopBar(
    onAction: (SettingsAction) -> Unit,
    scrollBehavior: NemlaTopBarScrollBehavior,
) {
    NemlaTopBar(
        title = { NemlaTopBarTitle(stringResource(Res.string.settings_title)) },
        navigationIcon = { NemlaBackButton(onClick = { onAction(SettingsAction.BackClicked) }) },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun EntryList(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onOpenPanel: (SettingsPanel) -> Unit,
) {
    val monthly = state.budget?.monthlyCents
    val budgetDetail =
        monthly?.takeIf { it > 0 }?.let {
            stringResource(
                Res.string.settings_entry_budget_amount,
                money(it, state.currency),
            )
        }
    NemlaCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Column(modifier = Modifier.padding(vertical = NemlaTheme.spacing.xs)) {
            Entry(Res.string.settings_entry_budget, Res.string.settings_entry_budget_help, NemlaIcons.Wallet, budgetDetail) {
                onOpenPanel(SettingsPanel.BUDGET)
            }
            EntryDivider()
            Entry(Res.string.settings_entry_recurring, Res.string.settings_entry_recurring_help, NemlaIcons.Repeat) {
                onOpenPanel(SettingsPanel.RECURRING)
            }
            EntryDivider()
            Entry(Res.string.settings_entry_profile, Res.string.settings_entry_profile_help, NemlaIcons.User) {
                onOpenPanel(SettingsPanel.PROFILE)
            }
            EntryDivider()
            Entry(Res.string.settings_entry_preferences, Res.string.settings_entry_preferences_help, NemlaIcons.Settings) {
                onOpenPanel(SettingsPanel.PREFERENCES)
            }
            EntryDivider()
            Entry(Res.string.settings_entry_card, Res.string.settings_entry_card_help, NemlaIcons.CreditCard) {
                onOpenPanel(SettingsPanel.CARD)
            }
        }
    }
    NemlaButton(
        text = stringResource(Res.string.settings_sign_out),
        onClick = { onAction(SettingsAction.SignOutClicked) },
        variant = NemlaButtonVariant.Outline,
        leadingIcon = NemlaIcons.LogOut,
        loading = state.isSigningOut,
        modifier = Modifier.fillMaxWidth(),
    )
    DangerZone(state, onAction)
}

@Composable
private fun EntryDivider() {
    HorizontalDivider(color = NemlaTheme.colors.border, modifier = Modifier.padding(start = EntryDividerInset))
}

private val EntryDividerInset = 72.dp

@Composable
private fun Entry(
    title: StringResource,
    subtitle: StringResource,
    icon: ImageVector,
    detail: String? = null,
    onClick: () -> Unit,
) {
    val colors = NemlaTheme.colors
    NemlaListItem(
        headline = stringResource(title),
        supportingContent = {
            Column {
                Text(stringResource(subtitle), style = NemlaTheme.typography.caption, color = colors.mutedForeground)
                if (detail != null) {
                    Text(detail, style = NemlaTheme.typography.caption.copy(fontFeatureSettings = "tnum"), color = colors.foreground)
                }
            }
        },
        leadingContent = { NemlaListIcon(icon) },
        trailingContent = {
            Icon(NemlaIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(NemlaTheme.sizes.iconLarge))
        },
        onClick = onClick,
    )
}

@Composable
private fun DangerZone(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    val colors = NemlaTheme.colors
    NemlaCard(modifier = Modifier.fillMaxWidth(), containerColor = colors.muted.copy(alpha = 0.3f)) {
        Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
            Text(
                stringResource(Res.string.settings_danger_title),
                style = NemlaTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                color = colors.mutedForeground,
            )
            Text(stringResource(Res.string.settings_danger_body), style = NemlaTheme.typography.caption, color = colors.mutedForeground)
            NemlaButton(
                text = stringResource(Res.string.settings_delete),
                onClick = { onAction(SettingsAction.DeleteAccountClicked) },
                variant = NemlaButtonVariant.Ghost,
                leadingIcon = NemlaIcons.Trash2,
                loading = state.isDeleting,
            )
        }
    }
}

@Composable
private fun DeleteDialog(onAction: (SettingsAction) -> Unit) {
    NemlaAlertDialog(
        title = stringResource(Res.string.settings_delete_confirm_title),
        text = stringResource(Res.string.settings_delete_confirm),
        confirmLabel = stringResource(Res.string.settings_delete),
        dismissLabel = stringResource(Res.string.settings_cancel),
        onConfirm = { onAction(SettingsAction.DeleteAccountConfirmed) },
        onDismissRequest = { onAction(SettingsAction.DeleteAccountCancelled) },
        icon = NemlaIcons.Trash2,
        destructive = true,
    )
}

private fun AccountMessage.resource() =
    when (this) {
        AccountMessage.DELETE_UNAVAILABLE -> Res.string.settings_delete_unavailable
        AccountMessage.DELETE_FAILED -> Res.string.settings_delete_failed
        AccountMessage.SIGN_OUT_FAILED -> Res.string.settings_sign_out_failed
    }

internal fun householdIcon(household: Household?): ImageVector =
    when (household) {
        Household.COUPLE -> NemlaIcons.HeartHandshake
        Household.FAMILY -> NemlaIcons.Baby
        Household.FLATSHARE -> NemlaIcons.HousePlus
        Household.SINGLE, null -> NemlaIcons.UserRound
    }
