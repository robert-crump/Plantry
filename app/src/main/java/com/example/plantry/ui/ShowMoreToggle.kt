package com.example.plantry.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.plantry.R

/** Rows a collapsed list shows, e.g. a recipe's ingredients or a Speiseplan week. */
internal const val COLLAPSED_ROWS = 5

/** How many of [count] rows show while collapsed: all of them, up to [COLLAPSED_ROWS]. */
internal fun collapsedCount(count: Int): Int = minOf(count, COLLAPSED_ROWS)

/**
 * The toggle under a collapsed list: [moreLabel], e.g. "+ 3 weitere Zutaten", while collapsed and
 * "Weniger anzeigen" while [expanded], each with a chevron.
 */
@Composable
fun ShowMoreToggle(expanded: Boolean, moreLabel: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (expanded) stringResource(R.string.action_show_less) else moreLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}
