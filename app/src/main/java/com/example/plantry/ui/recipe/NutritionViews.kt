package com.example.plantry.ui.recipe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.plantry.R
import com.example.plantry.data.Nutrient
import com.example.plantry.data.ProteinRating
import com.example.plantry.data.RecipeNutrition
import com.example.plantry.ui.cooklog.ProteinIcon
import com.example.plantry.ui.currentLocale
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Plant points come in quarters: "12", "12,25", "12,5" (German). */
internal fun formatPlantPoints(points: Double, locale: Locale): String =
    DecimalFormat("0.##", DecimalFormatSymbols(locale)).format(points)

/** Traffic light dot, protein per portion and the rating in words. */
@Composable
fun ProteinIndicator(proteinPerPortion: Double, modifier: Modifier = Modifier) {
    val rating = ProteinRating.of(proteinPerPortion)
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(rating.color, CircleShape),
        )
        Column {
            Text(
                stringResource(R.string.nutrition_protein_per_portion, formatNutrient(proteinPerPortion, Nutrient.PROTEIN, currentLocale())),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(rating.label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Every nutrient per portion, each with its icon. A row opens on tap to show the ingredients
 * contributing most to it; [expanded] holds the open ones.
 */
@Composable
fun NutrientList(nutrition: RecipeNutrition, expanded: Set<Nutrient>, onToggle: (Nutrient) -> Unit) {
    val locale = currentLocale()
    Column(Modifier.padding(vertical = 4.dp)) {
        Nutrient.entries.forEach { nutrient ->
            val contributors = nutrition.topContributors[nutrient].orEmpty()
            val open = nutrient in expanded && contributors.isNotEmpty()
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (contributors.isEmpty()) Modifier else Modifier.clickable { onToggle(nutrient) })
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(nutrient.icon, contentDescription = null)
                    Text(
                        stringResource(nutrient.displayName),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatRounded(nutrition.perPortion[nutrient], nutrient, locale),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    // Always takes its space, so the amounts line up in rows without contributors.
                    Box(Modifier.size(24.dp)) {
                        if (contributors.isNotEmpty()) {
                            Icon(
                                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = stringResource(if (open) R.string.nutrition_collapse else R.string.nutrition_expand),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                AnimatedVisibility(open) {
                    // Each entry is one unbreakable unit; a wide one moves to the next line as a whole.
                    FlowRow(
                        Modifier.padding(start = 40.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        contributors.forEachIndexed { index, contributor ->
                            val separator = if (index < contributors.lastIndex) "$NBSP·" else ""
                            Text(
                                "${contributor.name}$NBSP${formatRounded(contributor.amount, nutrient, locale)}$separator",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** kcal without decimals, grams with one, formatted for [locale]. */
fun formatNutrient(value: Double, nutrient: Nutrient, locale: Locale): String =
    String.format(locale, if (nutrient == Nutrient.KCAL) "%.0f" else "%.1f", value)

private const val NBSP = " "

/** Whole numbers with a non-breaking space before the unit; a nonzero amount under 1 reads "<1 g". */
internal fun formatRounded(value: Double, nutrient: Nutrient, locale: Locale): String {
    val unit = if (nutrient == Nutrient.KCAL) "kcal" else "g"
    val rounded = Math.round(value)
    val number = if (rounded == 0L && value > 0) "<1" else String.format(locale, "%d", rounded)
    return "$number$NBSP$unit"
}

private val ProteinRating.color: Color
    get() = when (this) {
        ProteinRating.GREEN -> Color(0xFF2E7D32)
        ProteinRating.YELLOW -> Color(0xFFF9A825)
        ProteinRating.RED -> Color(0xFFC62828)
    }

private val ProteinRating.label: Int
    get() = when (this) {
        ProteinRating.GREEN -> R.string.protein_rating_green
        ProteinRating.YELLOW -> R.string.protein_rating_yellow
        ProteinRating.RED -> R.string.protein_rating_red
    }

private val Nutrient.icon: Painter
    @Composable get() = when (this) {
        Nutrient.KCAL -> rememberVectorPainter(Icons.Filled.LocalFireDepartment)
        Nutrient.PROTEIN -> ProteinIcon
        Nutrient.CARBS -> rememberVectorPainter(Icons.Filled.BakeryDining)
        Nutrient.SUGAR -> rememberVectorPainter(Icons.Filled.Cookie)
        Nutrient.FAT -> rememberVectorPainter(Icons.Filled.WaterDrop)
        Nutrient.FIBRE -> rememberVectorPainter(Icons.Filled.Grass)
    }

private val Nutrient.displayName: Int
    get() = when (this) {
        Nutrient.KCAL -> R.string.nutrient_name_kcal
        Nutrient.PROTEIN -> R.string.nutrient_name_protein
        Nutrient.CARBS -> R.string.nutrient_name_carbs
        Nutrient.SUGAR -> R.string.nutrient_name_sugar
        Nutrient.FAT -> R.string.nutrient_name_fat
        Nutrient.FIBRE -> R.string.nutrient_name_fibre
    }
