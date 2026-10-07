package com.example.plantry.ui.cooklog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
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
 * grass with fibre, e.g. "5  520kcal  27g  9g". Kcal and fibre missing (old cooking log entries) show "–".
 * The stats behind [highlights] are green and bold. Compact, meant for its own line below a title;
 * [large] makes it half as big again, each stat left-aligned in a quarter of the width.
 */
@Composable
fun RecipeStatsRow(
    stats: RecipeStats,
    modifier: Modifier = Modifier,
    highlights: Set<RecipeHighlight> = emptySet(),
    large: Boolean = false,
) {
    val missing = stringResource(R.string.recipe_stats_missing)
    val scale = if (large) 1.5f else 1f
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val column = if (large) Modifier.weight(1f) else Modifier
        Stat(
            rememberVectorPainter(Icons.Filled.Eco),
            stringResource(R.string.recipe_sort_plant_points),
            stats.roundedPlantPoints.toString(),
            RecipeHighlight.PLANT_POINT_POWER.takeIf { it in highlights },
            scale,
            column,
        )
        Stat(
            rememberVectorPainter(Icons.Filled.LocalFireDepartment),
            stringResource(R.string.nutrient_name_kcal),
            stats.roundedKcal?.let { stringResource(R.string.recipe_stats_kcal, it) } ?: missing,
            RecipeHighlight.LOW_CALORIE.takeIf { it in highlights },
            scale,
            column,
        )
        Stat(
            ProteinIcon,
            stringResource(R.string.nutrient_name_protein),
            stringResource(R.string.recipe_stats_grams, stats.roundedProtein),
            RecipeHighlight.HIGH_PROTEIN.takeIf { it in highlights },
            scale,
            column,
        )
        Stat(
            rememberVectorPainter(Icons.Filled.Grass),
            stringResource(R.string.nutrient_name_fibre),
            stats.roundedFibre?.let { stringResource(R.string.recipe_stats_grams, it) } ?: missing,
            RecipeHighlight.HIGH_FIBRE.takeIf { it in highlights },
            scale,
            column,
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
    scale: Float,
    modifier: Modifier,
) {
    val label = highlight?.let { "$description, ${stringResource(it.label)}" } ?: description
    val green = highlightGreen
    val style = MaterialTheme.typography.bodySmall.let { it.copy(fontSize = it.fontSize * scale, lineHeight = it.lineHeight * scale) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp * scale), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = label,
            Modifier.size(14.dp * scale),
            tint = if (highlight != null) green else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = style,
            color = if (highlight != null) green else Color.Unspecified,
            fontWeight = if (highlight != null) FontWeight.Bold else null,
        )
    }
}

/** The highlight's name, e.g. "Proteinreich". */
val RecipeHighlight.label: Int
    get() = when (this) {
        RecipeHighlight.HIGH_PROTEIN -> R.string.highlight_high_protein
        RecipeHighlight.PLANT_POINT_POWER -> R.string.highlight_plant_point_power
        RecipeHighlight.LOW_CALORIE -> R.string.highlight_low_calorie
        RecipeHighlight.HIGH_FIBRE -> R.string.highlight_high_fibre
    }
