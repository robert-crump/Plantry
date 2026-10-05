package com.example.plantry.ui.cooklog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.LocalFireDepartment
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

/**
 * Per portion and rounded: leaf with plant points, flame with kcal, dumbbell with protein, grass
 * with fibre, e.g. "5  520  27g  9g". Kcal and fibre missing (old cooking log entries) show "–".
 * Compact, meant for its own line below a title.
 */
@Composable
fun RecipeStatsRow(stats: RecipeStats, modifier: Modifier = Modifier) {
    val missing = stringResource(R.string.recipe_stats_missing)
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stat(Icons.Filled.Eco, stringResource(R.string.recipe_sort_plant_points), stats.roundedPlantPoints.toString())
        Stat(
            Icons.Filled.LocalFireDepartment,
            stringResource(R.string.nutrient_name_kcal),
            stats.roundedKcal?.toString() ?: missing,
        )
        Stat(
            Icons.Filled.FitnessCenter,
            stringResource(R.string.nutrient_name_protein),
            stringResource(R.string.recipe_stats_grams, stats.roundedProtein),
        )
        Stat(
            Icons.Filled.Grass,
            stringResource(R.string.nutrient_name_fibre),
            stats.roundedFibre?.let { stringResource(R.string.recipe_stats_grams, it) } ?: missing,
        )
    }
}

@Composable
private fun Stat(icon: ImageVector, description: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = description, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
