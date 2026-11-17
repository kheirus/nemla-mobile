package app.nemla.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.nemla.core.designsystem.component.NemlaBackButton
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaEntryButton
import app.nemla.core.designsystem.component.NemlaOption
import app.nemla.core.designsystem.component.NemlaScreen
import app.nemla.core.designsystem.component.NemlaSegmentedControl
import app.nemla.core.designsystem.component.NemlaTopBar
import app.nemla.core.designsystem.component.NemlaTopBarBrand
import app.nemla.core.designsystem.component.NemlaTopBarScrollBehavior
import app.nemla.core.designsystem.component.NemlaTopBarTitle
import app.nemla.core.designsystem.component.rememberNemlaTopBarScrollBehavior
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.Household
import app.nemla.feature.settings.budget.BudgetPanelRoute
import app.nemla.feature.settings.card.CardPanelRoute
import app.nemla.feature.settings.categories.CategoriesTabRoute
import app.nemla.feature.settings.common.NoticeBanner
import app.nemla.feature.settings.common.NoticeKind
import app.nemla.feature.settings.common.money
import app.nemla.feature.settings.credits.CreditsTabRoute
import app.nemla.feature.settings.preferences.PreferencesPanelRoute
import app.nemla.feature.settings.profile.ProfilePanelRoute
import app.nemla.feature.settings.recurring.RecurringPanelRoute
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val PANEL_FADE_MS = 200

@Composable
internal fun SettingsRoute(
    initialTab: SettingsTab,
    onClose: () -> Unit,
    onSignedOut: () -> Unit,
    dictation: SettingsDictationSlot,
    reportHistory: @Composable () -> Unit,
    topLevel: Boolean = false,
    viewModel: SettingsViewModel = koinViewModel { parametersOf(initialTab) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnSignedOut by rememberUpdatedState(onSignedOut)
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
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.panel != null,
        onBackCompleted = { viewModel.onAction(SettingsAction.BackClicked) },
    )
    SettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        dictation = dictation,
        reportHistory = reportHistory,
        topLevel = topLevel,
    )
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    dictation: SettingsDictationSlot,
    reportHistory: @Composable () -> Unit,
    topLevel: Boolean = false,
) {
    val scrollBehavior = rememberNemlaTopBarScrollBehavior()
    NemlaScreen(topBar = { SettingsTopBar(state, onAction, topLevel, scrollBehavior) }, scrollBehavior = scrollBehavior) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md)) {
            NemlaSegmentedControl(
                options = settingsTabs(topLevel),
                selected = state.tab,
                onSelect = { onAction(SettingsAction.TabSelected(it)) },
                labelMaxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            state.accountMessage?.let { message ->
                NoticeBanner(text = stringResource(message.resource()), kind = NoticeKind.ERROR, onDismiss = {
                    onAction(SettingsAction.AccountMessageDismissed)
                })
            }
            when (state.tab) {
                SettingsTab.PROFILE -> ProfileTab(state, onAction, dictation)
                SettingsTab.REPORTS -> reportHistory()
                SettingsTab.CREDITS -> CreditsTabRoute()
                SettingsTab.CATEGORIES -> CategoriesTabRoute()
            }
        }
    }
    if (state.isConfirmingDelete) DeleteDialog(onAction)
}

@Composable
private fun settingsTabs(topLevel: Boolean): List<NemlaOption<SettingsTab>> =
    listOfNotNull(
        NemlaOption(SettingsTab.PROFILE, stringResource(Res.string.settings_tab_profile)),
        if (topLevel) null else NemlaOption(SettingsTab.REPORTS, stringResource(Res.string.settings_tab_reports)),
        NemlaOption(SettingsTab.CREDITS, stringResource(Res.string.settings_tab_credits)),
        NemlaOption(SettingsTab.CATEGORIES, stringResource(Res.string.settings_tab_categories)),
    )

@Composable
private fun SettingsTopBar(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    topLevel: Boolean,
    scrollBehavior: NemlaTopBarScrollBehavior,
) {
    val showBack = !topLevel || state.panel != null
    val title = stringResource(Res.string.settings_title)
    NemlaTopBar(
        title = { if (showBack) NemlaTopBarTitle(title) else NemlaTopBarBrand(title) },
        navigationIcon = {
            if (showBack) NemlaBackButton(onClick = { onAction(SettingsAction.BackClicked) })
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun ProfileTab(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    dictation: SettingsDictationSlot,
) {
    AnimatedContent(
        targetState = state.panel,
        transitionSpec = { fadeIn(tween(PANEL_FADE_MS)) togetherWith fadeOut(tween(PANEL_FADE_MS)) },
    ) { panel ->
        when (panel) {
            null -> EntryList(state, onAction)
            SettingsPanel.BUDGET -> BudgetPanelRoute(onOpenCategories = { onAction(SettingsAction.CategoryBudgetsRequested) })
            SettingsPanel.RECURRING -> RecurringPanelRoute(dictation = dictation)
            SettingsPanel.PROFILE -> ProfilePanelRoute(onProfileSaved = { onAction(SettingsAction.ProfileChanged) })
            SettingsPanel.PREFERENCES -> PreferencesPanelRoute()
            SettingsPanel.CARD -> CardPanelRoute()
        }
    }
}

@Composable
private fun EntryList(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    val monthly = state.budget?.monthlyCents
    Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm)) {
        Entry(
            Res.string.settings_entry_budget,
            Res.string.settings_entry_budget_help,
            NemlaIcons.Wallet,
            SettingsPanel.BUDGET,
            onAction,
            detail = monthly?.takeIf { it > 0 }?.let { stringResource(Res.string.settings_entry_budget_amount, money(it, state.currency)) },
        )
        Entry(
            Res.string.settings_entry_recurring,
            Res.string.settings_entry_recurring_help,
            NemlaIcons.Repeat,
            SettingsPanel.RECURRING,
            onAction,
        )
        Entry(Res.string.settings_entry_profile, Res.string.settings_entry_profile_help, NemlaIcons.User, SettingsPanel.PROFILE, onAction)
        Entry(
            Res.string.settings_entry_preferences,
            Res.string.settings_entry_preferences_help,
            NemlaIcons.Settings,
            SettingsPanel.PREFERENCES,
            onAction,
        )
        Entry(Res.string.settings_entry_card, Res.string.settings_entry_card_help, NemlaIcons.CreditCard, SettingsPanel.CARD, onAction)
        NemlaButton(
            text = stringResource(Res.string.settings_sign_out),
            onClick = { onAction(SettingsAction.SignOutClicked) },
            variant = NemlaButtonVariant.Outline,
            leadingIcon = NemlaIcons.LogOut,
            loading = state.isSigningOut,
            modifier = Modifier.fillMaxWidth().padding(top = NemlaTheme.spacing.xs),
        )
        DangerZone(state, onAction)
    }
}

@Composable
private fun Entry(
    title: StringResource,
    subtitle: StringResource,
    icon: ImageVector,
    panel: SettingsPanel,
    onAction: (SettingsAction) -> Unit,
    detail: String? = null,
) {
    NemlaEntryButton(
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        icon = icon,
        detail = detail,
        onClick = { onAction(SettingsAction.PanelOpened(panel)) },
    )
}

@Composable
private fun DangerZone(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    val colors = NemlaTheme.colors
    val shape = RoundedCornerShape(NemlaTheme.radius.xl)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = NemlaTheme.spacing.sm)
                .border(1.dp, colors.border.copy(alpha = 0.6f), shape)
                .background(colors.muted.copy(alpha = 0.3f), shape)
                .padding(NemlaTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
    ) {
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

@Composable
private fun DeleteDialog(onAction: (SettingsAction) -> Unit) {
    val colors = NemlaTheme.colors
    AlertDialog(
        onDismissRequest = { onAction(SettingsAction.DeleteAccountCancelled) },
        containerColor = colors.background,
        titleContentColor = colors.foreground,
        textContentColor = colors.mutedForeground,
        title = { Text(stringResource(Res.string.settings_delete_confirm_title), style = NemlaTheme.typography.cardTitle) },
        text = { Text(stringResource(Res.string.settings_delete_confirm), style = NemlaTheme.typography.body) },
        confirmButton = {
            NemlaButton(
                text = stringResource(Res.string.settings_delete),
                onClick = { onAction(SettingsAction.DeleteAccountConfirmed) },
                variant = NemlaButtonVariant.Destructive,
            )
        },
        dismissButton = {
            NemlaButton(
                text = stringResource(Res.string.settings_cancel),
                onClick = { onAction(SettingsAction.DeleteAccountCancelled) },
                variant = NemlaButtonVariant.Ghost,
            )
        },
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
