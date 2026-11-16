package app.nemla.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
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
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = NemlaTheme.colors
    val shape = RoundedCornerShape(NemlaTheme.radius.md)
    val borderColor =
        when {
            error != null -> colors.destructive
            focused -> colors.ring
            else -> colors.input
        }
    val borderWidth = if (focused || error != null) 2.dp else 1.dp
    val effectiveKeyboard =
        if (isPassword && keyboardOptions.keyboardType == KeyboardType.Unspecified) {
            keyboardOptions.copy(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
        } else {
            keyboardOptions
        }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.labelGap),
    ) {
        Text(
            text = label,
            style = NemlaTheme.typography.label,
            color = colors.foreground,
            modifier = Modifier.clearAndSetSemantics { },
        )
        BasicTextField(
            value = value,
            onValueChange = { next -> onValueChange(if (maxLength != null) next.take(maxLength) else next) },
            enabled = enabled,
            singleLine = true,
            textStyle = NemlaTheme.typography.input.copy(color = colors.foreground),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = effectiveKeyboard,
            keyboardActions = keyboardActions,
            visualTransformation =
                if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            interactionSource = interactionSource,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = label
                        if (error != null) error(error)
                        if (autofill != null) contentType = autofill.contentType
                    },
            decorationBox = { innerTextField ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = NemlaTheme.sizes.controlHeight)
                            .border(borderWidth, borderColor, shape)
                            .padding(start = NemlaTheme.spacing.sm, end = if (isPassword) 0.dp else NemlaTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = NemlaTheme.typography.input,
                                color = colors.mutedForeground,
                                modifier = Modifier.clearAndSetSemantics { },
                            )
                        }
                        innerTextField()
                    }
                    if (isPassword) {
                        PasswordToggle(visible = passwordVisible, onToggle = { passwordVisible = !passwordVisible })
                    }
                }
            },
        )
        if (error != null) {
            Text(
                text = error,
                style = NemlaTheme.typography.caption,
                color = colors.destructive,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

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
