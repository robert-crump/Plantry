package com.example.plantry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * [items] one below the other, [spacing] apart. A long press picks one up (it lifts, with a haptic
 * tick) to drag it among the others, never past the first or last; the others make room. Dropping
 * it elsewhere calls [onMove] with its position and that of the item it was dropped next to.
 *
 * [content] gets a modifier with the TalkBack actions [moveUpLabel] and [moveDownLabel], for the
 * node TalkBack focuses.
 */
@Composable
fun <T> DraggableColumn(
    items: List<T>,
    onMove: (from: Int, to: Int) -> Unit,
    moveUpLabel: String,
    moveDownLabel: String,
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    content: @Composable (item: T, actions: Modifier) -> Unit,
) {
    val currentOnMove by rememberUpdatedState(onMove)
    val count by rememberUpdatedState(items.size)
    val heights = remember { mutableStateMapOf<Int, Int>() }
    var dragged by remember { mutableStateOf<Int?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    val haptic = LocalHapticFeedback.current
    val target = dragged?.let { dropTarget(it, offset, count, spacingPx) { position -> heights[position] ?: 0 } }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.forEachIndexed { position, item ->
            val from = dragged
            val isDragged = position == from
            // The others between the dragged item and its target move into the room it leaves.
            val shift = when {
                from == null || target == null || isDragged -> 0f
                position in from + 1..target -> -((heights[from] ?: 0) + spacingPx)
                position in target until from -> (heights[from] ?: 0) + spacingPx
                else -> 0f
            }
            val actions = Modifier.semantics {
                customActions = listOfNotNull(
                    CustomAccessibilityAction(moveUpLabel) { currentOnMove(position, position - 1); true }
                        .takeIf { position > 0 },
                    CustomAccessibilityAction(moveDownLabel) { currentOnMove(position, position + 1); true }
                        .takeIf { position < items.lastIndex },
                )
            }
            Box(
                Modifier
                    .onSizeChanged { heights[position] = it.height }
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragged) offset else shift }
                    .then(
                        if (isDragged) {
                            Modifier
                                .shadow(8.dp, MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                        } else {
                            Modifier
                        },
                    )
                    .pointerInput(position) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragged = position
                                offset = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                // Kept within the column: from the top of the first item to the bottom of the last.
                                val gap = spacing.toPx()
                                val above = (0 until position).sumOf { (heights[it] ?: 0) + gap.toDouble() }
                                val below = (position + 1 until count).sumOf { (heights[it] ?: 0) + gap.toDouble() }
                                offset = (offset + amount.y).coerceIn(-above.toFloat(), below.toFloat())
                            },
                            onDragEnd = {
                                val to = dropTarget(position, offset, count, spacing.toPx()) { heights[it] ?: 0 }
                                dragged = null
                                offset = 0f
                                if (to != position) currentOnMove(position, to)
                            },
                            onDragCancel = {
                                dragged = null
                                offset = 0f
                            },
                        )
                    },
            ) {
                content(item, actions)
            }
        }
    }
}

/**
 * Where the item at [from], dragged by [offset] pixels, would drop among [count] items of [height]
 * and [spacing]: past each neighbour it is dragged over by more than half that neighbour.
 */
internal fun dropTarget(from: Int, offset: Float, count: Int, spacing: Float, height: (Int) -> Int): Int {
    var target = from
    var passed = 0f
    if (offset > 0) {
        while (target + 1 < count && offset > passed + (height(target + 1) + spacing) / 2) {
            passed += height(target + 1) + spacing
            target++
        }
    } else {
        while (target > 0 && -offset > passed + (height(target - 1) + spacing) / 2) {
            passed += height(target - 1) + spacing
            target--
        }
    }
    return target
}
