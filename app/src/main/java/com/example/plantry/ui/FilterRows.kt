package com.example.plantry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** One labelled row of filter chips, e.g. "Viel:" with its nutrient chips. */
class FilterRow(val label: String, val chips: @Composable FlowRowScope.() -> Unit)

/**
 * [rows] under each other, their labels in a column as wide as the widest, so every row's first
 * chip lines up; chips that don't fit wrap within their own column.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterRows(rows: List<FilterRow>, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    val labelWidth = with(LocalDensity.current) { rows.maxOf { measurer.measure(it.label, style).size.width }.toDp() }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    row.label,
                    style = style,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(labelWidth).alignByBaseline(),
                )
                FlowRow(
                    Modifier.weight(1f).alignByBaseline(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = row.chips,
                )
            }
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
