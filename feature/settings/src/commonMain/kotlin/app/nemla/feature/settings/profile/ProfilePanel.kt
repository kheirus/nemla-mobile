package app.nemla.feature.settings.profile

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaButtonVariant
import app.nemla.core.designsystem.component.NemlaChip
import app.nemla.core.designsystem.component.NemlaOption
import app.nemla.core.designsystem.component.NemlaSelectField
import app.nemla.core.designsystem.component.NemlaTextField
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.Gender
import app.nemla.core.domain.model.Household
import app.nemla.feature.settings.common.Caption
import app.nemla.feature.settings.common.NoticeBanner
import app.nemla.feature.settings.common.PanelCard
import app.nemla.feature.settings.householdIcon
import app.nemla.feature.settings.resources.Res
import app.nemla.feature.settings.resources.profile_age
import app.nemla.feature.settings.resources.profile_age_invalid
import app.nemla.feature.settings.resources.profile_done
import app.nemla.feature.settings.resources.profile_edit
import app.nemla.feature.settings.resources.profile_gender
import app.nemla.feature.settings.resources.profile_gender_female
import app.nemla.feature.settings.resources.profile_gender_male
import app.nemla.feature.settings.resources.profile_household
import app.nemla.feature.settings.resources.profile_household_couple
import app.nemla.feature.settings.resources.profile_household_family
import app.nemla.feature.settings.resources.profile_household_flatshare
import app.nemla.feature.settings.resources.profile_household_single
import app.nemla.feature.settings.resources.profile_not_set
import app.nemla.feature.settings.resources.profile_privacy
import app.nemla.feature.settings.resources.profile_pseudo
import app.nemla.feature.settings.resources.profile_pseudo_taken
import app.nemla.feature.settings.resources.profile_save
import app.nemla.feature.settings.resources.profile_save_failed
import app.nemla.feature.settings.resources.profile_saved
import app.nemla.feature.settings.resources.profile_suggestions
import app.nemla.feature.settings.resources.profile_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun ProfilePanelRoute(
    onProfileSaved: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSaved by rememberUpdatedState(onProfileSaved)
    LaunchedEffect(viewModel) {
        viewModel.onAction(ProfileAction.Opened)
        viewModel.effects.collect { effect ->
            when (effect) {
                ProfileEffect.Saved -> currentOnSaved()
            }
        }
    }
    ProfilePanel(state = state, onAction = viewModel::onAction)
}

@Composable
internal fun ProfilePanel(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.md)) {
        state.notice?.let { notice ->
            NoticeBanner(
                text = stringResource(notice.message.resource()),
                kind = notice.kind,
                onDismiss = { onAction(ProfileAction.NoticeDismissed) },
            )
        }
        PanelCard(title = stringResource(Res.string.profile_title)) {
            Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs), verticalAlignment = Alignment.Top) {
                Icon(NemlaIcons.ShieldCheck, contentDescription = null, tint = NemlaTheme.colors.primary, modifier = Modifier.size(16.dp))
                Caption(stringResource(Res.string.profile_privacy), modifier = Modifier.weight(1f))
            }
            if (state.isEditing) EditForm(state, onAction) else ReadOnly(state, onAction)
        }
    }
}

@Composable
private fun ReadOnly(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
) {
    val notSet = stringResource(Res.string.profile_not_set)
    val saved = state.saved
    NemlaButton(
        text = stringResource(Res.string.profile_edit),
        onClick = { onAction(ProfileAction.EditClicked) },
        variant = NemlaButtonVariant.Secondary,
        leadingIcon = NemlaIcons.Pencil,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
        Tile(stringResource(Res.string.profile_pseudo), saved.pseudo.ifBlank { notSet }, null)
        Tile(stringResource(Res.string.profile_age), saved.ageText.ifBlank { notSet }, null)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs)) {
        Tile(stringResource(Res.string.profile_gender), saved.gender?.let { stringResource(it.resource()) } ?: notSet, null)
        Tile(
            stringResource(Res.string.profile_household),
            saved.household?.let { stringResource(it.resource()) } ?: notSet,
            saved.household?.let(::householdIcon),
        )
    }
}

@Composable
private fun RowScope.Tile(
    label: String,
    value: String,
    icon: ImageVector?,
) {
    val shape = RoundedCornerShape(NemlaTheme.radius.xl)
    Column(
        modifier = Modifier.weight(1f).border(1.dp, NemlaTheme.colors.border, shape).padding(NemlaTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs),
    ) {
        Caption(label)
        Row(horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, contentDescription = null, tint = NemlaTheme.colors.accent, modifier = Modifier.size(16.dp))
            Text(value, style = NemlaTheme.typography.bodyStrong)
        }
    }
}

@Composable
private fun EditForm(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
) {
    val form = state.form
    NemlaTextField(
        value = form.pseudo,
        onValueChange = { onAction(ProfileAction.PseudoChanged(it)) },
        label = stringResource(Res.string.profile_pseudo),
    )
    if (state.suggestions.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Caption(stringResource(Res.string.profile_suggestions))
            state.suggestions.forEach { suggestion ->
                NemlaChip(text = suggestion, onClick = { onAction(ProfileAction.SuggestionPicked(suggestion)) })
            }
        }
    }
    NemlaTextField(
        value = form.ageText,
        onValueChange = { onAction(ProfileAction.AgeChanged(it)) },
        label = stringResource(Res.string.profile_age),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    NemlaSelectField(
        label = stringResource(Res.string.profile_gender),
        placeholder = stringResource(Res.string.profile_not_set),
        options = Gender.entries.map { NemlaOption(it, stringResource(it.resource())) },
        selected = form.gender,
        onSelect = { onAction(ProfileAction.GenderSelected(it)) },
    )
    NemlaSelectField(
        label = stringResource(Res.string.profile_household),
        placeholder = stringResource(Res.string.profile_not_set),
        options = Household.entries.map { NemlaOption(it, stringResource(it.resource())) },
        selected = form.household,
        onSelect = { onAction(ProfileAction.HouseholdSelected(it)) },
        leading = { household ->
            Icon(householdIcon(household), contentDescription = null, tint = NemlaTheme.colors.accent, modifier = Modifier.size(16.dp))
        },
    )
    NemlaButton(
        text = stringResource(Res.string.profile_save),
        onClick = { onAction(ProfileAction.SaveClicked) },
        loading = state.isSaving,
        modifier = Modifier.fillMaxWidth(),
    )
    NemlaButton(
        text = stringResource(Res.string.profile_done),
        onClick = { onAction(ProfileAction.DoneClicked) },
        variant = NemlaButtonVariant.Ghost,
        enabled = !state.isSaving,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun Gender.resource(): StringResource =
    when (this) {
        Gender.FEMALE -> Res.string.profile_gender_female
        Gender.MALE -> Res.string.profile_gender_male
    }

private fun Household.resource(): StringResource =
    when (this) {
        Household.SINGLE -> Res.string.profile_household_single
        Household.COUPLE -> Res.string.profile_household_couple
        Household.FAMILY -> Res.string.profile_household_family
        Household.FLATSHARE -> Res.string.profile_household_flatshare
    }

private fun ProfileMessage.resource(): StringResource =
    when (this) {
        ProfileMessage.SAVED -> Res.string.profile_saved
        ProfileMessage.PSEUDO_TAKEN -> Res.string.profile_pseudo_taken
        ProfileMessage.AGE_INVALID -> Res.string.profile_age_invalid
        ProfileMessage.SAVE_FAILED -> Res.string.profile_save_failed
    }
