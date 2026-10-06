package com.example.plantry.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ThumbHeight = 48.dp
private val BubbleGap = 4.dp
private const val HideDelayMillis = 1_500L

/**
 * A draggable scrollbar for the lazy list driven by [state]. It shows while the list scrolls and
 * fades out [HideDelayMillis] after it stops; while the thumb is dragged, a bubble above it shows
 * [label] for the item at the top of the list (by index; null shows no bubble). Place it over the
 * list's right edge with the list's height. Only the thumb takes touches, and only while visible.
 */
@Composable
fun FastScrollbar(state: LazyListState, label: (index: Int) -> String?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }
    val active = state.isScrollInProgress || dragging
    LaunchedEffect(active) {
        if (active) {
            visible = true
        } else {
            delay(HideDelayMillis)
            visible = false
        }
    }
    val scrollable by remember(state) { derivedStateOf { state.canScrollForward || state.canScrollBackward } }
    val fraction by remember(state) { derivedStateOf { state.scrollFraction() } }
    val alpha by animateFloatAsState(if (visible && scrollable) 1f else 0f, label = "scrollbar alpha")

    BoxWithConstraints(modifier) {
        val trackPx = constraints.maxHeight - with(LocalDensity.current) { ThumbHeight.toPx() }
        if (trackPx <= 0f) return@BoxWithConstraints
        val thumbFraction = if (dragging) dragFraction else fraction
        val thumbY = (thumbFraction * trackPx).roundToInt()
        val text = if (dragging) label(state.firstVisibleItemIndex) else null
        if (text != null) {
            // Right above the thumb, sharing its right edge, so the thumb never hides it; near the
            // top it may reach over whatever is above the list.
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, thumbY - placeable.height - BubbleGap.roundToPx())
                        }
                    },
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbY) }
                .graphicsLayer { this.alpha = alpha }
                .size(width = 24.dp, height = ThumbHeight)
                .then(
                    if (visible && scrollable) {
                        Modifier.pointerInput(state, trackPx) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragFraction = fraction
                                    dragging = true
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                            ) { change, dy ->
                                change.consume()
                                dragFraction = (dragFraction + dy / trackPx).coerceIn(0f, 1f)
                                val target = dragFraction
                                scope.launch { state.scrollToFraction(target) }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = if (dragging) 8.dp else 6.dp, height = ThumbHeight - 8.dp)
                    .background(
                        if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        RoundedCornerShape(4.dp),
                    ),
            )
        }
    }
}

private fun LazyListState.scrollFraction(): Float {
    if (!canScrollForward) return if (canScrollBackward) 1f else 0f
    val info = layoutInfo
    val average = info.averageItemSize() ?: return 0f
    return scrollFraction(firstVisibleItemIndex, firstVisibleItemScrollOffset, average, info.totalItemsCount, info.contentViewport())
}

private suspend fun LazyListState.scrollToFraction(fraction: Float) {
    val info = layoutInfo
    val average = info.averageItemSize() ?: return
    val (index, offset) = scrollTarget(fraction, average, info.totalItemsCount, info.contentViewport())
    scrollToItem(index, offset)
}

private fun LazyListLayoutInfo.averageItemSize(): Float? =
    visibleItemsInfo.takeIf { it.isNotEmpty() }?.let { items -> items.sumOf { it.size }.toFloat() / items.size }

private fun LazyListLayoutInfo.contentViewport(): Int =
    viewportSize.height - beforeContentPadding - afterContentPadding

/**
 * How far the list is scrolled, 0..1, estimating every item at [averageSize]: the scrolled pixels
 * over the scroll range ([totalItems] items minus the [viewport]).
 */
internal fun scrollFraction(firstIndex: Int, firstOffset: Int, averageSize: Float, totalItems: Int, viewport: Int): Float {
    val range = averageSize * totalItems - viewport
    if (range <= 0f) return 0f
    return ((firstIndex * averageSize + firstOffset) / range).coerceIn(0f, 1f)
}

/** The item index and offset to scroll to for [fraction]; the inverse of [scrollFraction]. */
internal fun scrollTarget(fraction: Float, averageSize: Float, totalItems: Int, viewport: Int): Pair<Int, Int> {
    if (totalItems == 0) return 0 to 0
    val pixels = fraction.coerceIn(0f, 1f) * (averageSize * totalItems - viewport).coerceAtLeast(0f)
    val index = (pixels / averageSize).toInt().coerceIn(0, totalItems - 1)
    return index to (pixels - index * averageSize).roundToInt().coerceAtLeast(0)
}
