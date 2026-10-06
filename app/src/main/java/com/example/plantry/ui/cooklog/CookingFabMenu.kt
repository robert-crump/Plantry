package com.example.plantry.ui.cooklog

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.plantry.R

/**
 * The Kochen FAB: a "+" that turns into an X when tapped and shows "Vorschlag" and "Kocheintrag"
 * above it. Hidden while [visible] is false (scrolling down) unless open; back closes it.
 */
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
    AnimatedVisibility(
        visible = visible || expanded,
        modifier = modifier,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            MenuItem(expanded, stringResource(R.string.suggest_action), Icons.Filled.AutoAwesome) {
                onExpandedChange(false)
                onSuggest()
            }
            MenuItem(expanded, stringResource(R.string.cook_log_action), Icons.Filled.EditNote) {
                onExpandedChange(false)
                onLog()
            }
            // The "+" turned by 45° is the X.
            val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = "fab rotation")
            FloatingActionButton(onClick = { onExpandedChange(!expanded) }) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(if (expanded) R.string.cook_actions_close else R.string.cook_actions_open),
                    modifier = Modifier.rotate(rotation),
                )
            }
        }
    }
}

@Composable
private fun MenuItem(visible: Boolean, text: String, icon: ImageVector, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        ExtendedFloatingActionButton(
            text = { Text(text) },
            icon = { Icon(icon, contentDescription = null) },
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
