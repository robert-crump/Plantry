package com.example.plantry.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Scrolls [state] to the top, then calls [onScrolled], whenever [key] changes, e.g. a new sort or
 * filter. Not when the list comes back from a detail screen with the key it already scrolled for,
 * so the list stays where it was. The key is kept as its text, which survives the saved state.
 */
@Composable
fun ScrollToTopOnChange(state: LazyListState, key: Any?, onScrolled: () -> Unit = {}) {
    var scrolledFor by rememberSaveable { mutableStateOf<String?>(null) }
    val current = key.toString()
    LaunchedEffect(current) {
        if (current == scrolledFor) return@LaunchedEffect
        scrolledFor = current
        state.scrollToItem(0)
        onScrolled()
    }
}
