package com.example.plantry.ui.cooklog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.plantry.R
import com.example.plantry.data.RecipeStats

/** Leaf with plant points, dumbbell with protein, flash with carbs; all rounded, e.g. "5  27g  48g". */
@Composable
fun RecipeStatsRow(stats: RecipeStats, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stat(Icons.Filled.Eco, stringResource(R.string.recipe_sort_plant_points), stats.roundedPlantPoints.toString())
        Stat(
            Icons.Filled.FitnessCenter,
            stringResource(R.string.nutrient_name_protein),
            stringResource(R.string.recipe_stats_grams, stats.roundedProtein),
        )
        Stat(
            Icons.Filled.Bolt,
            stringResource(R.string.nutrient_name_carbs),
            stringResource(R.string.recipe_stats_grams, stats.roundedCarbs),
        )
    }
}

@Composable
private fun Stat(icon: ImageVector, description: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = description, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
