package app.nemla.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import app.nemla.core.designsystem.theme.NemlaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> NemlaSegmentedButtons(
    options: List<NemlaOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NemlaTheme.colors
    val scheme = MaterialTheme.colorScheme
    val buttonColors =
        SegmentedButtonDefaults.colors(
            activeContainerColor = scheme.secondaryContainer,
            activeContentColor = scheme.onSecondaryContainer,
            activeBorderColor = colors.input,
            inactiveContainerColor = Color.Transparent,
            inactiveContentColor = colors.foreground,
            inactiveBorderColor = colors.input,
        )
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option.value == selected,
                onClick = { onSelect(option.value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = buttonColors,
                label = {
                    Text(
                        text = option.label,
                        style = NemlaTheme.typography.button,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
