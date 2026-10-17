package com.example.plantry.ui.cooklog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.plantry.R
import com.example.plantry.data.RecipeHighlight
import com.example.plantry.data.RecipeStats
import com.example.plantry.ui.theme.highlightGreen

/**
 * Per portion and rounded: leaf with plant points, flame with kcal, [ProteinIcon] with protein,
 * croissant with carbs, grass with fibre, e.g. "5  520kcal  27g  64g  9g". Kcal and fibre missing (old
 * cooking log entries) show "–". The stats behind [highlights] are green and bold. Compact, meant for
 * its own line below a title.
 */
@Composable
fun RecipeStatsRow(
    stats: RecipeStats,
    modifier: Modifier = Modifier,
    highlights: Set<RecipeHighlight> = emptySet(),
) {
    val missing = stringResource(R.string.recipe_stats_missing)
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stat(
            RecipeHighlight.PLANT_POINT_POWER.icon,
            stringResource(R.string.recipe_sort_plant_points),
            stats.roundedPlantPoints.toString(),
            RecipeHighlight.PLANT_POINT_POWER.takeIf { it in highlights },
        )
        Stat(
            RecipeHighlight.LOW_CALORIE.icon,
            stringResource(R.string.nutrient_name_kcal),
            stats.roundedKcal?.let { stringResource(R.string.recipe_stats_kcal, it) } ?: missing,
            RecipeHighlight.LOW_CALORIE.takeIf { it in highlights },
        )
        Stat(
            RecipeHighlight.HIGH_PROTEIN.icon,
            stringResource(R.string.nutrient_name_protein),
            stringResource(R.string.recipe_stats_grams, stats.roundedProtein),
            RecipeHighlight.HIGH_PROTEIN.takeIf { it in highlights },
        )
        Stat(
            RecipeHighlight.HIGH_CARB.icon,
            stringResource(R.string.nutrient_name_carbs),
            stringResource(R.string.recipe_stats_grams, stats.roundedCarbs),
            RecipeHighlight.HIGH_CARB.takeIf { it in highlights },
        )
        Stat(
            RecipeHighlight.HIGH_FIBRE.icon,
            stringResource(R.string.nutrient_name_fibre),
            stats.roundedFibre?.let { stringResource(R.string.recipe_stats_grams, it) } ?: missing,
            RecipeHighlight.HIGH_FIBRE.takeIf { it in highlights },
        )
    }
}

/** The app's protein icon: Material Symbols "Exercise", which the Compose icon library lacks. */
val ProteinIcon: Painter
    @Composable get() = painterResource(R.drawable.ic_exercise)

/** A highlighted stat is green and bold, and its name joins the description so screen readers say why. */
@Composable
private fun Stat(
    icon: Painter,
    description: String,
    value: String,
    highlight: RecipeHighlight?,
) {
    val label = highlight?.let { "$description, ${stringResource(it.label)}" } ?: description
    val green = highlightGreen
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = label,
            Modifier.size(14.dp),
            tint = if (highlight != null) green else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlight != null) green else Color.Unspecified,
            fontWeight = if (highlight != null) FontWeight.Bold else null,
            // Clipped rather than broken into a column of single digits when space runs out.
            softWrap = false,
        )
    }
}

/** The highlight's name, e.g. "Protein-Power", the same on its tag and its filter chip. */
val RecipeHighlight.label: Int
    get() = when (this) {
        RecipeHighlight.PLANT_POINT_POWER -> R.string.highlight_plant_point_power
        RecipeHighlight.HIGH_PROTEIN -> R.string.filter_protein
        RecipeHighlight.HIGH_CARB -> R.string.filter_carbs
        RecipeHighlight.HIGH_FIBRE -> R.string.filter_fibre
        RecipeHighlight.LOW_CALORIE -> R.string.highlight_low_calorie
    }

/** The stats row's icon for the stat behind the highlight. */
val RecipeHighlight.icon: Painter
    @Composable get() = when (this) {
        RecipeHighlight.PLANT_POINT_POWER -> rememberVectorPainter(Icons.Filled.Eco)
        RecipeHighlight.HIGH_PROTEIN -> ProteinIcon
        RecipeHighlight.HIGH_CARB -> rememberVectorPainter(Icons.Filled.BakeryDining)
        RecipeHighlight.HIGH_FIBRE -> rememberVectorPainter(Icons.Filled.Grass)
        RecipeHighlight.LOW_CALORIE -> rememberVectorPainter(Icons.Filled.LocalFireDepartment)
    }
