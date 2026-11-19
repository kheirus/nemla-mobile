package app.nemla.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private const val LIST_MOTION_MS = 250

@Composable
fun <T> NemlaAnimatedColumn(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    itemContent: @Composable (index: Int, item: T, first: Boolean) -> Unit,
) {
    val memory = remember { AnimatedColumnMemory<T>() }
    var generation by remember { mutableIntStateOf(0) }
    val shown = remember(items, generation) { memory.merge(items, key) }
    val liveKeys = remember(items) { items.mapTo(HashSet(), key) }
    Column(modifier = modifier) {
        shown.forEachIndexed { index, item ->
            val itemKey = key(item)
            key(itemKey) {
                val visibility = remember { MutableTransitionState(!memory.started).apply { targetState = true } }
                val alive = itemKey in liveKeys
                SideEffect { visibility.targetState = alive }
                if (!alive && visibility.isIdle && !visibility.currentState) {
                    SideEffect {
                        memory.forget(itemKey)
                        generation++
                    }
                }
                AnimatedVisibility(
                    visibleState = visibility,
                    enter = expandVertically(tween(LIST_MOTION_MS)) + fadeIn(tween(LIST_MOTION_MS)),
                    exit =
                        slideOutHorizontally(tween(LIST_MOTION_MS)) { it / 3 } +
                            shrinkVertically(tween(LIST_MOTION_MS)) +
                            fadeOut(tween(LIST_MOTION_MS)),
                ) {
                    itemContent(index, item, index == 0)
                }
            }
        }
    }
    SideEffect { memory.started = true }
}

private class AnimatedColumnMemory<T> {
    var started = false
    private var shown: List<T> = emptyList()
    private val forgotten = HashSet<Any>()

    fun merge(
        items: List<T>,
        key: (T) -> Any,
    ): List<T> {
        val live = items.mapTo(HashSet(), key)
        val result = items.toMutableList()
        shown.forEachIndexed { index, item ->
            val itemKey = key(item)
            if (itemKey !in live && itemKey !in forgotten) result.add(index.coerceAtMost(result.size), item)
        }
        forgotten.clear()
        shown = result
        return result
    }

    fun forget(itemKey: Any) {
        forgotten.add(itemKey)
    }
}
