package app.nemla.feature.dashboard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToLong

internal const val COUNT_UP_MILLIS = 600
internal const val DONUT_SWEEP_MILLIS = 900
internal const val BAR_RISE_MILLIS = 500
internal const val BAR_STAGGER_MILLIS = 30

@Composable
internal fun animatedCents(
    target: Long,
    animationKey: Any,
): Long {
    val progress = remember { Animatable(0f) }
    var from by remember { mutableLongStateOf(0L) }
    var to by remember { mutableLongStateOf(0L) }
    var animatedFor by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(target, animationKey) {
        if (animatedFor == animationKey) {
            from = target
            to = target
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        from = interpolate(from, to, progress.value)
        to = target
        animatedFor = animationKey
        progress.snapTo(0f)
        progress.animateTo(1f, tween(COUNT_UP_MILLIS, easing = FastOutSlowInEasing))
    }
    return if (animatedFor == animationKey && !progress.isRunning) target else interpolate(from, to, progress.value)
}

internal fun staggeredFraction(
    elapsedMillis: Float,
    index: Int,
): Float {
    val local = ((elapsedMillis - index * BAR_STAGGER_MILLIS) / BAR_RISE_MILLIS).coerceIn(0f, 1f)
    return FastOutSlowInEasing.transform(local)
}

private fun interpolate(
    from: Long,
    to: Long,
    fraction: Float,
): Long = from + ((to - from) * fraction.toDouble()).roundToLong()
