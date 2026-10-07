package com.example.plantry.ui.cooklog

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.plantry.R

/**
 * The Kochen FAB: the Material 3 FAB menu, a "+" that turns into an X when tapped and shows
 * "Vorschlag" and "Kocheintrag" above it. Hidden while [visible] is false (scrolling down) unless
 * open; back closes it. The dimming behind it is [FabScrim].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CookingFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    visible: Boolean,
    onSuggest: () -> Unit,
    onLog: () -> Unit,
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
        FloatingActionButtonMenuItem(
            onClick = {
                onExpandedChange(false)
                onSuggest()
            },
            text = { Text(stringResource(R.string.suggest_action)) },
            icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
        )
        FloatingActionButtonMenuItem(
            onClick = {
                onExpandedChange(false)
                onLog()
            },
            text = { Text(stringResource(R.string.cook_log_action)) },
            icon = { Icon(Icons.Filled.EditNote, contentDescription = null) },
        )
    }
}

/** Dims the content while the FAB menu is open; tapping it closes the menu. */
@Composable
fun FabScrim(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        )
    }
}
