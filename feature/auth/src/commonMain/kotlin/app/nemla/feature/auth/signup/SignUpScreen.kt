package app.nemla.feature.auth.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nemla.core.designsystem.component.NemlaAutofill
import app.nemla.core.designsystem.component.NemlaBanner
import app.nemla.core.designsystem.component.NemlaBannerKind
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaOption
import app.nemla.core.designsystem.component.NemlaSelectField
import app.nemla.core.designsystem.component.NemlaTextField
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.core.domain.model.Gender
import app.nemla.core.domain.model.Household
import app.nemla.feature.auth.common.AuthBannerView
import app.nemla.feature.auth.common.AuthLayout
import app.nemla.feature.auth.common.CollectEffects
import app.nemla.feature.auth.common.fieldErrorText
import app.nemla.feature.auth.resources.Res
import app.nemla.feature.auth.resources.auth_age
import app.nemla.feature.auth.resources.auth_choose
import app.nemla.feature.auth.resources.auth_create_account
import app.nemla.feature.auth.resources.auth_email
import app.nemla.feature.auth.resources.auth_gender
import app.nemla.feature.auth.resources.auth_gender_female
import app.nemla.feature.auth.resources.auth_gender_male
import app.nemla.feature.auth.resources.auth_household
import app.nemla.feature.auth.resources.auth_household_couple
import app.nemla.feature.auth.resources.auth_household_family
import app.nemla.feature.auth.resources.auth_household_flatshare
import app.nemla.feature.auth.resources.auth_household_single
import app.nemla.feature.auth.resources.auth_password
import app.nemla.feature.auth.resources.auth_privacy
import app.nemla.feature.auth.resources.auth_pseudo
import app.nemla.feature.auth.resources.auth_signup_subtitle
import app.nemla.feature.auth.resources.auth_signup_title
import app.nemla.feature.auth.resources.auth_submitting
import app.nemla.feature.auth.resources.auth_switch_to_sign_in
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SignUpRoute(
    onSignedUp: () -> Unit,
    onOpenSignIn: () -> Unit,
    viewModel: SignUpViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            SignUpEffect.SignedUp -> onSignedUp()
            SignUpEffect.OpenSignIn -> onOpenSignIn()
        }
    }
    SignUpScreen(state = state, onAction = viewModel::onAction)
}

@Composable
internal fun SignUpScreen(
    state: SignUpUiState,
    onAction: (SignUpAction) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val editable = !state.isSubmitting
    val next = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    AuthLayout(
        title = stringResource(Res.string.auth_signup_title),
        subtitle = stringResource(Res.string.auth_signup_subtitle),
        footerLink = stringResource(Res.string.auth_switch_to_sign_in),
        onFooterLink = { onAction(SignUpAction.SignInClicked) },
        footerEnabled = editable,
    ) {
        NemlaTextField(
            value = state.email,
            onValueChange = { onAction(SignUpAction.EmailChanged(it)) },
            label = stringResource(Res.string.auth_email),
            error = fieldErrorText(state.emailError),
            enabled = editable,
            autofill = NemlaAutofill.Email,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
            keyboardActions = next,
        )
        NemlaTextField(
            value = state.password,
            onValueChange = { onAction(SignUpAction.PasswordChanged(it)) },
            label = stringResource(Res.string.auth_password),
            error = fieldErrorText(state.passwordError),
            enabled = editable,
            isPassword = true,
            autofill = NemlaAutofill.NewPassword,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            keyboardActions = next,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            NemlaTextField(
                value = state.pseudo,
                onValueChange = { onAction(SignUpAction.PseudoChanged(it)) },
                label = stringResource(Res.string.auth_pseudo),
                enabled = editable,
                autofill = NemlaAutofill.NewUsername,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
                keyboardActions = next,
                modifier = Modifier.weight(1f),
            )
            NemlaTextField(
                value = state.age,
                onValueChange = { onAction(SignUpAction.AgeChanged(it)) },
                label = stringResource(Res.string.auth_age),
                error = fieldErrorText(state.ageError),
                enabled = editable,
                maxLength = AGE_MAX_DIGITS,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.weight(1f),
            )
        }
        NemlaSelectField(
            label = stringResource(Res.string.auth_gender),
            placeholder = stringResource(Res.string.auth_choose),
            options = genderOptions(),
            selected = state.gender,
            onSelect = { onAction(SignUpAction.GenderSelected(it)) },
            enabled = editable,
        )
        NemlaSelectField(
            label = stringResource(Res.string.auth_household),
            placeholder = stringResource(Res.string.auth_choose),
            options = householdOptions(),
            selected = state.household,
            onSelect = { onAction(SignUpAction.HouseholdSelected(it)) },
            enabled = editable,
        )
        NemlaBanner(
            message = stringResource(Res.string.auth_privacy),
            kind = NemlaBannerKind.Neutral,
            icon = NemlaIcons.ShieldCheck,
            announce = false,
        )
        AuthBannerView(state.banner)
        NemlaButton(
            text = stringResource(if (state.isSubmitting) Res.string.auth_submitting else Res.string.auth_create_account),
            onClick = {
                focusManager.clearFocus()
                onAction(SignUpAction.Submit)
            },
            loading = state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun genderOptions(): List<NemlaOption<Gender>> =
    listOf(
        NemlaOption(Gender.FEMALE, stringResource(Res.string.auth_gender_female)),
        NemlaOption(Gender.MALE, stringResource(Res.string.auth_gender_male)),
    )

@Composable
private fun householdOptions(): List<NemlaOption<Household>> =
    listOf(
        NemlaOption(Household.SINGLE, stringResource(Res.string.auth_household_single)),
        NemlaOption(Household.COUPLE, stringResource(Res.string.auth_household_couple)),
        NemlaOption(Household.FAMILY, stringResource(Res.string.auth_household_family)),
        NemlaOption(Household.FLATSHARE, stringResource(Res.string.auth_household_flatshare)),
    )

@Preview
@Composable
private fun SignUpScreenPreview() {
    NemlaTheme {
        SignUpScreen(state = SignUpUiState(), onAction = {})
    }
}
