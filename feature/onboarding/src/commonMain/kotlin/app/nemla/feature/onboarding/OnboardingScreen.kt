package app.nemla.feature.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.nemla.core.designsystem.component.NemlaAppHeader
import app.nemla.core.designsystem.component.NemlaBanner
import app.nemla.core.designsystem.component.NemlaBannerKind
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaCard
import app.nemla.core.designsystem.component.NemlaIconButton
import app.nemla.core.designsystem.component.NemlaMenuItem
import app.nemla.core.designsystem.component.NemlaOption
import app.nemla.core.designsystem.component.NemlaScreen
import app.nemla.core.designsystem.component.NemlaSelectField
import app.nemla.core.designsystem.component.NemlaTextArea
import app.nemla.core.designsystem.component.NemlaTextField
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.ChargeFrequency
import app.nemla.core.domain.model.Household
import app.nemla.feature.onboarding.resources.Res
import app.nemla.feature.onboarding.resources.onboarding_account_menu
import app.nemla.feature.onboarding.resources.onboarding_amount
import app.nemla.feature.onboarding.resources.onboarding_analysis_failed
import app.nemla.feature.onboarding.resources.onboarding_analyze
import app.nemla.feature.onboarding.resources.onboarding_budget_label
import app.nemla.feature.onboarding.resources.onboarding_budget_placeholder
import app.nemla.feature.onboarding.resources.onboarding_category
import app.nemla.feature.onboarding.resources.onboarding_dismiss
import app.nemla.feature.onboarding.resources.onboarding_frequency
import app.nemla.feature.onboarding.resources.onboarding_input_label
import app.nemla.feature.onboarding.resources.onboarding_input_placeholder
import app.nemla.feature.onboarding.resources.onboarding_intro
import app.nemla.feature.onboarding.resources.onboarding_monthly
import app.nemla.feature.onboarding.resources.onboarding_my_space
import app.nemla.feature.onboarding.resources.onboarding_name
import app.nemla.feature.onboarding.resources.onboarding_nothing_detected
import app.nemla.feature.onboarding.resources.onboarding_offline
import app.nemla.feature.onboarding.resources.onboarding_remove
import app.nemla.feature.onboarding.resources.onboarding_review_title
import app.nemla.feature.onboarding.resources.onboarding_save
import app.nemla.feature.onboarding.resources.onboarding_save_failed
import app.nemla.feature.onboarding.resources.onboarding_sign_out
import app.nemla.feature.onboarding.resources.onboarding_title
import app.nemla.feature.onboarding.resources.onboarding_yearly
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val NOTICE_DURATION_MS = 5_000L

@Composable
internal fun OnboardingRoute(
    onFinished: () -> Unit,
    onSignedOut: () -> Unit,
    dictation: OnboardingDictationSlot,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnSignedOut by rememberUpdatedState(onSignedOut)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    OnboardingEffect.Finished -> currentOnFinished()
                    OnboardingEffect.SignedOut -> currentOnSignedOut()
                }
            }
        }
    }
    OnboardingScreen(state = state, onAction = viewModel::onAction, dictation = dictation)
}

@Composable
internal fun OnboardingScreen(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    dictation: OnboardingDictationSlot,
) {
    NemlaScreen(topBar = { OnboardingHeader(state, onAction) }) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
            Text(
                stringResource(Res.string.onboarding_title),
                style = NemlaTheme.typography.screenTitle,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(Res.string.onboarding_intro), style = NemlaTheme.typography.body, color = NemlaTheme.colors.mutedForeground)
        }
        state.notice?.let { notice -> NoticeBanner(notice, onAction) }
        InputCard(state, onAction, dictation)
        if (state.rows.isNotEmpty()) ReviewCard(state, onAction)
        BudgetCard(state, onAction)
    }
}

@Composable
private fun OnboardingHeader(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
) {
    NemlaAppHeader(
        accountLabel = state.pseudo ?: stringResource(Res.string.onboarding_my_space),
        avatar = householdIcon(state.household),
        menuDescription = stringResource(Res.string.onboarding_account_menu),
    ) { dismiss ->
        NemlaMenuItem(
            text = stringResource(Res.string.onboarding_sign_out),
            icon = NemlaIcons.LogOut,
            enabled = !state.isSigningOut,
            onClick = {
                dismiss()
                onAction(OnboardingAction.SignOutClicked)
            },
        )
    }
}

@Composable
private fun NoticeBanner(
    notice: OnboardingNotice,
    onAction: (OnboardingAction) -> Unit,
) {
    LaunchedEffect(notice) {
        delay(NOTICE_DURATION_MS)
        onAction(OnboardingAction.NoticeDismissed)
    }
    val (text, kind) =
        when (notice) {
            is OnboardingNotice.Dictation -> notice.text to NemlaBannerKind.Error
            is OnboardingNotice.Known -> stringResource(notice.message.resource()) to notice.message.kind()
        }
    val dismiss = stringResource(Res.string.onboarding_dismiss)
    NemlaBanner(
        message = text,
        kind = kind,
        modifier = Modifier.clickable(onClickLabel = dismiss) { onAction(OnboardingAction.NoticeDismissed) },
    )
}

@Composable
private fun InputCard(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    dictation: OnboardingDictationSlot,
) {
    NemlaCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm)) {
            NemlaTextArea(
                value = state.text,
                onValueChange = { onAction(OnboardingAction.TextChanged(it)) },
                label = stringResource(Res.string.onboarding_input_label),
                placeholder = stringResource(Res.string.onboarding_input_placeholder),
                minLines = 4,
                maxLength = 4_000,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs), verticalAlignment = Alignment.Top) {
                NemlaButton(
                    text = stringResource(Res.string.onboarding_analyze),
                    onClick = { onAction(OnboardingAction.AnalyzeClicked) },
                    enabled = state.canAnalyze,
                    loading = state.isAnalyzing,
                    leadingIcon = NemlaIcons.Sparkles,
                )
                dictation(
                    !state.isAnalyzing && !state.isSaving,
                    { onAction(OnboardingAction.Transcribed(it)) },
                    { onAction(OnboardingAction.DictationActiveChanged(it)) },
                    { onAction(OnboardingAction.DictationMessage(it)) },
                )
            }
        }
    }
}

@Composable
private fun ReviewCard(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
) {
    val categoryOptions = state.categories.map { NemlaOption(it.id, it.name) }
    val frequencyOptions =
        listOf(
            NemlaOption(ChargeFrequency.MONTHLY, stringResource(Res.string.onboarding_monthly)),
            NemlaOption(ChargeFrequency.YEARLY, stringResource(Res.string.onboarding_yearly)),
        )
    NemlaCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm)) {
            Text(
                stringResource(Res.string.onboarding_review_title),
                style = NemlaTheme.typography.cardTitle,
                modifier = Modifier.semantics { heading() },
            )
            state.rows.forEach { row ->
                NemlaCard(modifier = Modifier.fillMaxWidth(), contentPadding = NemlaTheme.spacing.sm) {
                    Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
                            NemlaTextField(
                                value = row.name,
                                onValueChange = { onAction(OnboardingAction.RowNameChanged(row.key, it)) },
                                label = stringResource(Res.string.onboarding_name),
                                labelVisible = false,
                                placeholder = stringResource(Res.string.onboarding_name),
                                modifier = Modifier.weight(1f),
                            )
                            NemlaTextField(
                                value = row.amountText,
                                onValueChange = { onAction(OnboardingAction.RowAmountChanged(row.key, it)) },
                                label = stringResource(Res.string.onboarding_amount),
                                labelVisible = false,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(AMOUNT_WIDTH),
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NemlaSelectField(
                                label = stringResource(Res.string.onboarding_category),
                                placeholder = stringResource(Res.string.onboarding_category),
                                options = categoryOptions,
                                selected = row.categoryId,
                                onSelect = { onAction(OnboardingAction.RowCategorySelected(row.key, it)) },
                                labelVisible = false,
                                modifier = Modifier.weight(1f),
                            )
                            NemlaSelectField(
                                label = stringResource(Res.string.onboarding_frequency),
                                placeholder = stringResource(Res.string.onboarding_frequency),
                                options = frequencyOptions,
                                selected = row.frequency,
                                onSelect = { onAction(OnboardingAction.RowFrequencySelected(row.key, it)) },
                                labelVisible = false,
                                modifier = Modifier.width(FREQUENCY_WIDTH),
                            )
                            NemlaIconButton(
                                icon = NemlaIcons.Trash2,
                                contentDescription = stringResource(Res.string.onboarding_remove),
                                onClick = { onAction(OnboardingAction.RowRemoved(row.key)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetCard(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
) {
    NemlaCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm)) {
            NemlaTextField(
                value = state.budgetText,
                onValueChange = { onAction(OnboardingAction.BudgetChanged(it)) },
                label = stringResource(Res.string.onboarding_budget_label, state.currency.symbol),
                placeholder = stringResource(Res.string.onboarding_budget_placeholder),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            NemlaButton(
                text = stringResource(Res.string.onboarding_save),
                onClick = { onAction(OnboardingAction.SaveClicked) },
                loading = state.isSaving,
                enabled = !state.isAnalyzing && !state.isDictating,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val AMOUNT_WIDTH = 104.dp
private val FREQUENCY_WIDTH = 144.dp

internal fun householdIcon(household: Household?) =
    when (household) {
        Household.COUPLE -> NemlaIcons.HeartHandshake
        Household.FAMILY -> NemlaIcons.Baby
        Household.FLATSHARE -> NemlaIcons.HousePlus
        Household.SINGLE, null -> NemlaIcons.UserRound
    }

private fun OnboardingMessage.resource(): StringResource =
    when (this) {
        OnboardingMessage.ANALYSIS_FAILED -> Res.string.onboarding_analysis_failed
        OnboardingMessage.NOTHING_DETECTED -> Res.string.onboarding_nothing_detected
        OnboardingMessage.SAVE_FAILED -> Res.string.onboarding_save_failed
        OnboardingMessage.OFFLINE -> Res.string.onboarding_offline
    }

private fun OnboardingMessage.kind(): NemlaBannerKind =
    if (this ==
        OnboardingMessage.NOTHING_DETECTED
    ) {
        NemlaBannerKind.Info
    } else {
        NemlaBannerKind.Error
    }

@Preview
@Composable
private fun OnboardingScreenPreview() {
    NemlaTheme {
        OnboardingScreen(
            state =
                OnboardingUiState(
                    text = "Netflix 15, loyer 750 le 5",
                    rows = listOf(ChargeRow(0, "Netflix", "15,00", null, ChargeFrequency.MONTHLY, 1)),
                ),
            onAction = {},
            dictation = { _, _, _, _ -> },
        )
    }
}
