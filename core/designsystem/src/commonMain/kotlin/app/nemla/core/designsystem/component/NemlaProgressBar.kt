package app.nemla.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nemla.core.designsystem.theme.NemlaTheme

private const val TRACK_ALPHA = 0.2f
private const val FILL_DURATION_MILLIS = 700

@Composable
fun NemlaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val colors = NemlaTheme.colors
    val clamped = progress.coerceIn(0f, 1f)
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val shown by animateFloatAsState(
        targetValue = if (appeared) clamped else 0f,
        animationSpec = tween(FILL_DURATION_MILLIS, easing = FastOutSlowInEasing),
    )
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(shape)
                .background(colors.primary.copy(alpha = TRACK_ALPHA))
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f) },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(shown)
                    .clip(shape)
                    .background(colors.primary),
        )
    }
}
