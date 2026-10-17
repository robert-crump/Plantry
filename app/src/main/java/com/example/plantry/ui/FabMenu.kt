package com.example.plantry.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.plantry.R

/** One action in a [FabMenu]. */
class FabMenuAction(@StringRes val label: Int, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The Material 3 FAB menu: a "+" that turns into an X when tapped and shows [actions] above it, top
 * to bottom. Hidden while [visible] is false (scrolling down) unless open; back closes it. Taps
 * outside it land on [FabScrim].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    visible: Boolean,
    actions: List<FabMenuAction>,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                modifier = Modifier.animateFloatingActionButton(
                    visible = visible || expanded,
                    alignment = Alignment.BottomEnd,
                ),
            ) {
                Icon(
                    if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = stringResource(if (expanded) R.string.cook_actions_close else R.string.cook_actions_open),
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        actions.forEach { action ->
            FloatingActionButtonMenuItem(
                onClick = {
                    onExpandedChange(false)
                    action.onClick()
                },
                text = { Text(stringResource(action.label)) },
                icon = { Icon(action.icon, contentDescription = null) },
            )
        }
    }
}

/**
 * Covers the content while the FAB menu is open, invisibly: tapping it closes the menu and the tap
 * goes no further. Not dimmed, since it can't reach the app bar and status bar.
 */
@Composable
fun FabScrim(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (visible) {
        Box(
            modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        )
    }
}

/**
 * [content] (a Scaffold) with [snackbar] at the bottom and the FAB lifted above it by the Dp
 * [content] is given (the Scaffold would put the snackbar above the FAB), so the snackbar is laid
 * over the Scaffold instead of using its snackbarHost slot.
 */
@Composable
fun SnackbarBelowFab(snackbar: SnackbarHostState, content: @Composable BoxScope.(fabLift: Dp) -> Unit) {
    var snackbarHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // Less the snackbar's own 12dp margin, so the FAB keeps its usual 16dp gap.
    val fabLift by animateDpAsState(
        with(density) { (snackbarHeight.toDp() - 12.dp).coerceAtLeast(0.dp) },
        label = "fab lift",
    )
    Box(Modifier.fillMaxSize()) {
        content(fabLift)
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .onSizeChanged { snackbarHeight = it.height },
        )
    }
}
