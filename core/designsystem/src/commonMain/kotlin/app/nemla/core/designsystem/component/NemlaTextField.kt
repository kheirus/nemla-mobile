package app.nemla.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import app.nemla.core.designsystem.icon.NemlaIcons
import app.nemla.core.designsystem.resources.Res
import app.nemla.core.designsystem.resources.ds_password_hide
import app.nemla.core.designsystem.resources.ds_password_show
import app.nemla.core.designsystem.theme.NemlaTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun NemlaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isPassword: Boolean = false,
    maxLength: Int? = null,
    autofill: NemlaAutofill? = null,
    labelVisible: Boolean = true,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val effectiveKeyboard =
        if (isPassword && keyboardOptions.keyboardType == KeyboardType.Unspecified) {
            keyboardOptions.copy(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
        } else {
            keyboardOptions
        }
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(if (maxLength != null) next.take(maxLength) else next) },
        enabled = enabled,
        singleLine = true,
        textStyle = NemlaTheme.typography.input,
        label = if (labelVisible) ({ Text(label) }) else null,
        placeholder = placeholder?.let { text -> { Text(text, style = NemlaTheme.typography.input) } },
        trailingIcon =
            if (isPassword) {
                { PasswordToggle(visible = passwordVisible, onToggle = { passwordVisible = !passwordVisible }) }
            } else {
                null
            },
        isError = error != null,
        supportingText = error?.let { text -> { Text(text) } },
        keyboardOptions = effectiveKeyboard,
        keyboardActions = keyboardActions,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(NemlaTheme.radius.md),
        colors = nemlaOutlinedFieldColors(),
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    if (!labelVisible) contentDescription = label
                    if (autofill != null) contentType = autofill.contentType
                },
    )
}

@Composable
internal fun nemlaOutlinedFieldColors(): TextFieldColors {
    val colors = NemlaTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.foreground,
        unfocusedTextColor = colors.foreground,
        disabledTextColor = colors.foreground.copy(alpha = DISABLED_ALPHA),
        errorTextColor = colors.foreground,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        cursorColor = colors.primary,
        errorCursorColor = colors.destructive,
        focusedBorderColor = colors.ring,
        unfocusedBorderColor = colors.input,
        disabledBorderColor = colors.input.copy(alpha = DISABLED_ALPHA),
        errorBorderColor = colors.destructive,
        focusedLabelColor = colors.ring,
        unfocusedLabelColor = colors.mutedForeground,
        disabledLabelColor = colors.mutedForeground.copy(alpha = DISABLED_ALPHA),
        errorLabelColor = colors.destructive,
        focusedPlaceholderColor = colors.mutedForeground,
        unfocusedPlaceholderColor = colors.mutedForeground,
        focusedTrailingIconColor = colors.mutedForeground,
        unfocusedTrailingIconColor = colors.mutedForeground,
        errorTrailingIconColor = colors.mutedForeground,
        errorSupportingTextColor = colors.destructive,
    )
}

private const val DISABLED_ALPHA = 0.5f

enum class NemlaAutofill(
    internal val contentType: ContentType,
) {
    Email(ContentType.EmailAddress),
    Password(ContentType.Password),
    NewPassword(ContentType.NewPassword),
    NewUsername(ContentType.NewUsername),
}

@Composable
private fun PasswordToggle(
    visible: Boolean,
    onToggle: () -> Unit,
) {
    val description = stringResource(if (visible) Res.string.ds_password_hide else Res.string.ds_password_show)
    IconButton(onClick = onToggle, modifier = Modifier.size(NemlaTheme.sizes.minTouchTarget)) {
        Icon(
            imageVector = if (visible) NemlaIcons.EyeOff else NemlaIcons.Eye,
            contentDescription = description,
            tint = NemlaTheme.colors.mutedForeground,
            modifier = Modifier.size(NemlaTheme.sizes.icon),
        )
    }
}
