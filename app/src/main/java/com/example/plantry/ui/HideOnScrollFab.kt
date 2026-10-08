package com.example.plantry.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Whether a list's FAB shows: hidden while the list scrolls down, back as soon as it scrolls up.
 * Attach [connection] with `Modifier.nestedScroll` around the list.
 */
class FabVisibility {
    var visible by mutableStateOf(true)

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < 0f) visible = false else if (available.y > 0f) visible = true
            return Offset.Zero
        }
    }
}

@Composable
fun rememberFabVisibility() = remember { FabVisibility() }

/** [fab] slides down off the screen while hidden. */
@Composable
fun HideOnScrollFab(visibility: FabVisibility, fab: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visibility.visible,
        // Twice the FAB's height also clears the margin below it.
        enter = slideInVertically(tween(FAB_SLIDE_MILLIS)) { it * 2 },
        exit = slideOutVertically(tween(FAB_SLIDE_MILLIS)) { it * 2 },
    ) { fab() }
}

private const val FAB_SLIDE_MILLIS = 150
