package app.nemla.feature.auth.forgot

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.nemla.core.designsystem.component.NemlaAutofill
import app.nemla.core.designsystem.component.NemlaButton
import app.nemla.core.designsystem.component.NemlaTextField
import app.nemla.core.designsystem.theme.NemlaTheme
import app.nemla.feature.auth.common.AuthBannerView
import app.nemla.feature.auth.common.AuthLayout
import app.nemla.feature.auth.common.CollectEffects
import app.nemla.feature.auth.common.fieldErrorText
import app.nemla.feature.auth.resources.Res
import app.nemla.feature.auth.resources.auth_email
import app.nemla.feature.auth.resources.auth_forgot_subtitle
import app.nemla.feature.auth.resources.auth_forgot_title
import app.nemla.feature.auth.resources.auth_send_link
import app.nemla.feature.auth.resources.auth_submitting
import app.nemla.feature.auth.resources.auth_switch_to_sign_up
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun ForgotPasswordRoute(
    onLinkSent: () -> Unit,
    onOpenSignUp: () -> Unit,
    viewModel: ForgotPasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            ForgotPasswordEffect.LinkSent -> onLinkSent()
            ForgotPasswordEffect.OpenSignUp -> onOpenSignUp()
        }
    }
    ForgotPasswordScreen(state = state, onAction = viewModel::onAction)
}

@Composable
internal fun ForgotPasswordScreen(
    state: ForgotPasswordUiState,
    onAction: (ForgotPasswordAction) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    AuthLayout(
        title = stringResource(Res.string.auth_forgot_title),
        subtitle = stringResource(Res.string.auth_forgot_subtitle),
        footerLink = stringResource(Res.string.auth_switch_to_sign_up),
        onFooterLink = { onAction(ForgotPasswordAction.SignUpClicked) },
        footerEnabled = !state.isSubmitting,
    ) {
        NemlaTextField(
            value = state.email,
            onValueChange = { onAction(ForgotPasswordAction.EmailChanged(it)) },
            label = stringResource(Res.string.auth_email),
            error = fieldErrorText(state.emailError),
            enabled = !state.isSubmitting,
            autofill = NemlaAutofill.Email,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Send,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
            keyboardActions =
                KeyboardActions(onSend = {
                    focusManager.clearFocus()
                    onAction(ForgotPasswordAction.Submit)
                }),
        )
        AuthBannerView(state.banner)
        NemlaButton(
            text = stringResource(if (state.isSubmitting) Res.string.auth_submitting else Res.string.auth_send_link),
            onClick = {
                focusManager.clearFocus()
                onAction(ForgotPasswordAction.Submit)
            },
            loading = state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun ForgotPasswordScreenPreview() {
    NemlaTheme {
        ForgotPasswordScreen(state = ForgotPasswordUiState(), onAction = {})
    }
}
