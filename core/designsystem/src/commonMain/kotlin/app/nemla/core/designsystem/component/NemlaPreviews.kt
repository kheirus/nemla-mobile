package app.nemla.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.nemla.core.designsystem.brand.NemlaLogo
import app.nemla.core.designsystem.theme.NemlaTheme

@Preview
@Composable
private fun ComponentsLightPreview() {
    NemlaTheme(darkTheme = false) { ComponentsSample() }
}

@Preview
@Composable
private fun ComponentsDarkPreview() {
    NemlaTheme(darkTheme = true) { ComponentsSample() }
}

@Composable
private fun ComponentsSample() {
    NemlaScreen {
        NemlaLogo()
        NemlaCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(NemlaTheme.spacing.fieldGap)) {
                NemlaTextField(value = "", onValueChange = {}, label = PREVIEW_LABEL, placeholder = PREVIEW_PLACEHOLDER)
                NemlaTextField(value = PREVIEW_VALUE, onValueChange = {}, label = PREVIEW_LABEL, isPassword = true)
                NemlaTextField(value = PREVIEW_VALUE, onValueChange = {}, label = PREVIEW_LABEL, error = PREVIEW_ERROR)
                NemlaSelectField(
                    label = PREVIEW_LABEL,
                    placeholder = PREVIEW_PLACEHOLDER,
                    options = listOf(NemlaOption(1, PREVIEW_VALUE)),
                    selected = null,
                    onSelect = {},
                )
                NemlaBanner(message = PREVIEW_ERROR, kind = NemlaBannerKind.Error)
                NemlaBanner(message = PREVIEW_VALUE, kind = NemlaBannerKind.Neutral)
                NemlaButton(text = PREVIEW_VALUE, onClick = {}, modifier = Modifier.fillMaxWidth())
                NemlaButton(text = PREVIEW_VALUE, onClick = {}, loading = true, modifier = Modifier.fillMaxWidth())
                NemlaButton(text = PREVIEW_VALUE, onClick = {}, variant = NemlaButtonVariant.Secondary)
                NemlaTextButton(text = PREVIEW_VALUE, onClick = {})
            }
        }
    }
}

private const val PREVIEW_LABEL = "Label"
private const val PREVIEW_PLACEHOLDER = "Placeholder"
private const val PREVIEW_VALUE = "Value"
private const val PREVIEW_ERROR = "Error"
