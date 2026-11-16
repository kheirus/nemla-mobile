package app.nemla.feature.auth.signin

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
import app.nemla.core.common.ValidationCode
import app.nemla.core.designsystem.component.NemlaAutofill
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaTextButton
import app.nemla.core.designsystem.component.NemlaTextField
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.feature.auth.common.AuthBannerView
import app.nemla.feature.auth.common.AuthLayout
import app.nemla.feature.auth.common.CollectEffects
import app.nemla.feature.auth.common.fieldErrorText
import app.nemla.feature.auth.resources.Res
import app.nemla.feature.auth.resources.auth_email
import app.nemla.feature.auth.resources.auth_forgot_link
import app.nemla.feature.auth.resources.auth_password
import app.nemla.feature.auth.resources.auth_sign_in
import app.nemla.feature.auth.resources.auth_signin_subtitle
import app.nemla.feature.auth.resources.auth_signin_title
import app.nemla.feature.auth.resources.auth_submitting
import app.nemla.feature.auth.resources.auth_switch_to_sign_up
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun SignInRoute(
    resetLinkSent: Boolean,
    onSignedIn: () -> Unit,
    onOpenForgotPassword: () -> Unit,
    onOpenSignUp: () -> Unit,
    viewModel: SignInViewModel = koinViewModel { parametersOf(resetLinkSent) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            SignInEffect.SignedIn -> onSignedIn()
            SignInEffect.OpenForgotPassword -> onOpenForgotPassword()
            SignInEffect.OpenSignUp -> onOpenSignUp()
        }
    }
    SignInScreen(state = state, onAction = viewModel::onAction)
}

@Composable
internal fun SignInScreen(
    state: SignInUiState,
    onAction: (SignInAction) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    AuthLayout(
        title = stringResource(Res.string.auth_signin_title),
        subtitle = stringResource(Res.string.auth_signin_subtitle),
        footerLink = stringResource(Res.string.auth_switch_to_sign_up),
        onFooterLink = { onAction(SignInAction.SignUpClicked) },
        footerEnabled = !state.isSubmitting,
    ) {
        NemlaTextField(
            value = state.email,
            onValueChange = { onAction(SignInAction.EmailChanged(it)) },
            label = stringResource(Res.string.auth_email),
            error = fieldErrorText(state.emailError),
            enabled = !state.isSubmitting,
            autofill = NemlaAutofill.Email,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        )
        NemlaTextField(
            value = state.password,
            onValueChange = { onAction(SignInAction.PasswordChanged(it)) },
            label = stringResource(Res.string.auth_password),
            error = fieldErrorText(state.passwordError),
            enabled = !state.isSubmitting,
            isPassword = true,
            autofill = NemlaAutofill.Password,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    onAction(SignInAction.Submit)
                }),
        )
        NemlaTextButton(
            text = stringResource(Res.string.auth_forgot_link),
            onClick = { onAction(SignInAction.ForgotPasswordClicked) },
            enabled = !state.isSubmitting,
            modifier = Modifier.align(Alignment.Start),
        )
        AuthBannerView(state.banner)
        NemlaButton(
            text = stringResource(if (state.isSubmitting) Res.string.auth_submitting else Res.string.auth_sign_in),
            onClick = {
                focusManager.clearFocus()
                onAction(SignInAction.Submit)
            },
            loading = state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun SignInScreenPreview() {
    NemlaTheme {
        SignInScreen(state = SignInUiState(emailError = ValidationCode.EMAIL_INVALID), onAction = {})
    }
}
