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
 * Lists shorter than [MinViewports] viewports get no scrollbar.
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
    val cache = remember(state) { ItemSizeCache() }
    val sizes by remember(state) { derivedStateOf { cache.update(state.layoutInfo) } }
    val scrollable by remember(state) {
        derivedStateOf { (state.canScrollForward || state.canScrollBackward) && showsScrollbar(sizes, state.layoutInfo.contentViewport()) }
    }
    val fraction by remember(state) {
        derivedStateOf {
            if (!state.canScrollForward) {
                if (state.canScrollBackward) 1f else 0f
            } else {
                scrollFraction(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, sizes, state.layoutInfo.contentViewport())
            }
        }
    }
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
                                scope.launch {
                                    val (index, offset) = scrollTarget(target, sizes, state.layoutInfo.contentViewport())
                                    state.scrollToItem(index, offset)
                                }
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

/**
 * Remembers each item's laid-out size (plus the list's item spacing) by index, so the scrollbar's
 * estimate of the list height doesn't swing as items of different heights scroll in and out.
 * Forgets everything when the item count changes or an index shows a different key.
 */
private class ItemSizeCache {
    private val sizes = HashMap<Int, Int>()
    private val keys = HashMap<Int, Any>()
    private var count = -1

    fun update(info: LazyListLayoutInfo): ItemSizes {
        if (info.totalItemsCount != count) {
            sizes.clear()
            keys.clear()
            count = info.totalItemsCount
        }
        if (info.visibleItemsInfo.any { item -> keys[item.index].let { it != null && it != item.key } }) {
            sizes.clear()
            keys.clear()
        }
        for (item in info.visibleItemsInfo) {
            sizes[item.index] = item.size + info.mainAxisItemSpacing
            keys[item.index] = item.key
        }
        return ItemSizes(sizes.toMap(), count)
    }
}

private fun LazyListLayoutInfo.contentViewport(): Int =
    viewportSize.height - beforeContentPadding - afterContentPadding

/**
 * The sizes of [totalItems] items: the [known] ones by index, every other one at the average of
 * the known ones.
 */
internal class ItemSizes(private val known: Map<Int, Int>, val totalItems: Int) {
    private val average = if (known.isEmpty()) 0f else known.values.sum().toFloat() / known.size

    fun sizeOf(index: Int): Float = known[index]?.toFloat() ?: average

    /** Where item [index] starts, in pixels from the top of the list. */
    fun offsetOf(index: Int): Float {
        val knownBefore = known.filterKeys { it < index }
        return knownBefore.values.sum() + (index - knownBefore.size) * average
    }

    val total: Float get() = offsetOf(totalItems)

    /** The item at [pixels] from the top and how far into it [pixels] reaches. */
    fun itemAt(pixels: Float): Pair<Int, Int> {
        if (totalItems == 0) return 0 to 0
        var start = 0f
        for (index in 0 until totalItems) {
            val size = sizeOf(index)
            // Within half a pixel of the next item counts as that item, so rounding never lands
            // on the very end of this one.
            if (pixels + 0.5f < start + size || index == totalItems - 1) {
                return index to (pixels - start).roundToInt().coerceAtLeast(0)
            }
            start += size
        }
        return totalItems - 1 to 0
    }
}

/** Shorter lists scroll without a scrollbar: dragging a thumb over a short range is too twitchy. */
private const val MinViewports = 3

/** Whether a list of [sizes] is long enough, at least [MinViewports] of [viewport], for a scrollbar. */
internal fun showsScrollbar(sizes: ItemSizes, viewport: Int): Boolean =
    viewport > 0 && sizes.total >= MinViewports * viewport

/** How far the list is scrolled, 0..1: the scrolled pixels over the scroll range ([sizes] minus the [viewport]). */
internal fun scrollFraction(firstIndex: Int, firstOffset: Int, sizes: ItemSizes, viewport: Int): Float {
    val range = sizes.total - viewport
    if (range <= 0f) return 0f
    return ((sizes.offsetOf(firstIndex) + firstOffset) / range).coerceIn(0f, 1f)
}

/** The item index and offset to scroll to for [fraction]; the inverse of [scrollFraction]. */
internal fun scrollTarget(fraction: Float, sizes: ItemSizes, viewport: Int): Pair<Int, Int> =
    sizes.itemAt(fraction.coerceIn(0f, 1f) * (sizes.total - viewport).coerceAtLeast(0f))
