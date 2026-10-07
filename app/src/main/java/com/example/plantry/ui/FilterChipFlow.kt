package com.example.plantry.ui

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.plantry.R

private val ChipMove = BoundsTransform { _, _ -> tween(250) }

/**
 * Filter chips on as many rows as they need: [active] first, in the order they were switched on,
 * then [extra] (e.g. "+ Zutat"), then [inactive] in their home order. A tap toggles a chip, and
 * chips slide to their new place. From two active chips on, a ✕ chip in front clears them all.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun <T : Any> FilterChipFlow(
    active: List<T>,
    inactive: List<T>,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
) {
    LookaheadScope {
        val moving = Modifier.animateBounds(this, boundsTransform = ChipMove)
        FlowRow(
            modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (active.size >= 2) {
                key("clear") {
                    AssistChip(
                        onClick = onClear,
                        label = { Icon(Icons.Filled.Close, stringResource(R.string.filter_clear), Modifier.size(FilterChipDefaults.IconSize)) },
                        modifier = moving,
                    )
                }
            }
            active.forEach { chip ->
                key(chip) {
                    FilterChip(
                        selected = true,
                        onClick = { onToggle(chip) },
                        label = { Text(label(chip)) },
                        leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
                        modifier = moving,
                    )
                }
            }
            if (extra != null) key("extra") { Box(moving) { extra() } }
            inactive.forEach { chip ->
                key(chip) {
                    FilterChip(selected = false, onClick = { onToggle(chip) }, label = { Text(label(chip)) }, modifier = moving)
                }
            }
        }
    }
}
