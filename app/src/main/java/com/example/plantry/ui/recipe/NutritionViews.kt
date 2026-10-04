package com.example.plantry.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.plantry.R
import com.example.plantry.data.Nutrient
import com.example.plantry.data.ProteinRating
import com.example.plantry.data.RecipeNutrition
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Plant points come in quarters: "12", "12,25", "12,5". */
internal fun formatPlantPoints(points: Double): String =
    DecimalFormat("0.##", DecimalFormatSymbols(Locale.GERMAN)).format(points)

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
                stringResource(R.string.nutrition_protein_per_portion, formatNutrient(proteinPerPortion, Nutrient.PROTEIN)),
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

/** Every nutrient per portion with the ingredients contributing most to it. */
@Composable
fun NutrientList(nutrition: RecipeNutrition) {
    Column {
        Nutrient.entries.forEach { nutrient ->
            val contributors = nutrition.topContributors[nutrient].orEmpty()
            ListItem(
                headlineContent = { Text(stringResource(nutrient.displayName)) },
                supportingContent = if (contributors.isEmpty()) {
                    null
                } else {
                    {
                        Text(
                            contributors.joinToString(" · ") { "${it.name} ${formatNutrientWithUnit(it.amount, nutrient)}" },
                        )
                    }
                },
                trailingContent = {
                    Text(
                        formatNutrientWithUnit(nutrition.perPortion[nutrient], nutrient),
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
            )
        }
    }
}

/** kcal without decimals, grams with one, German formatting. */
fun formatNutrient(value: Double, nutrient: Nutrient): String =
    String.format(Locale.GERMANY, if (nutrient == Nutrient.KCAL) "%.0f" else "%.1f", value)

private fun formatNutrientWithUnit(value: Double, nutrient: Nutrient): String =
    formatNutrient(value, nutrient) + if (nutrient == Nutrient.KCAL) " kcal" else " g"

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

private val Nutrient.displayName: Int
    get() = when (this) {
        Nutrient.KCAL -> R.string.nutrient_name_kcal
        Nutrient.PROTEIN -> R.string.nutrient_name_protein
        Nutrient.CARBS -> R.string.nutrient_name_carbs
        Nutrient.SUGAR -> R.string.nutrient_name_sugar
        Nutrient.FAT -> R.string.nutrient_name_fat
        Nutrient.FIBRE -> R.string.nutrient_name_fibre
    }
