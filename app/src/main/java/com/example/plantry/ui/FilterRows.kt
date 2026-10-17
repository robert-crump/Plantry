package com.example.plantry.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One row of filter chips, e.g. the nutrient chips. */
typealias FilterRow = @Composable RowScope.() -> Unit

/**
 * A chip is 32dp tall inside a 48dp touch target; overlapping the targets by this much leaves
 * the same visible 8dp between chips above each other as beside each other.
 */
private val ChipRowSpacing = (-8).dp

/**
 * [rows] under each other, each on one line that scrolls sideways when its chips don't fit; the
 * chips start at the margin and scroll out to the screen edge.
 */
@Composable
fun FilterRows(rows: List<FilterRow>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ChipRowSpacing)) {
        rows.forEach { chips ->
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = chips,
            )
        }
    }
}

/**
 * A filter chip a tap switches on or off; on shows in its colour and bold, without a tick. The
 * label always takes its bold width, so no chip moves when one is switched.
 */
@Composable
fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Box(contentAlignment = Alignment.Center) {
                Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(0f).clearAndSetSemantics {})
                Text(label, fontWeight = if (selected) FontWeight.Bold else null)
            }
        },
    )
}
