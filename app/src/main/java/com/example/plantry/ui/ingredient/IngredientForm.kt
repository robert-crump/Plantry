package com.example.plantry.ui.ingredient

import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientDraft
import com.example.plantry.data.Nutrient
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Raw input of the ingredient detail screen. Decimals accept both "," and ".". */
data class IngredientForm(
    val name: String = "",
    val nutrition: Map<Nutrient, String> = Nutrient.entries.associateWith { "" },
    val storeSection: StoreSection = StoreSection.OTHER,
    val plantPoints: PlantPoints = PlantPoints.ZERO,
) {
    fun withNutrient(nutrient: Nutrient, value: String) = copy(nutrition = nutrition + (nutrient to value))

    fun errors() = IngredientFormErrors(
        name = name.isBlank(),
        nutrients = Nutrient.entries.filterTo(mutableSetOf()) { nutrient ->
            nutrition[nutrient].orEmpty().toDecimalOrNull().let { it == null || it < 0 }
        },
    )

    /** Returns the validated draft, or null if any field is invalid. */
    fun toDraft(): IngredientDraft? {
        if (errors().hasAny) return null
        return IngredientDraft(
            name = name,
            nutrition = Nutrition.of(nutrition.mapValues { it.value.toDecimalOrNull()!! }),
            storeSection = storeSection,
            plantPoints = plantPoints,
        )
    }

    companion object {
        fun from(ingredient: Ingredient) = IngredientForm(
            name = ingredient.name,
            nutrition = Nutrient.entries.associateWith { formatDecimal(ingredient.nutrition[it]) },
            storeSection = ingredient.storeSection,
            plantPoints = ingredient.plantPoints,
        )
    }
}

data class IngredientFormErrors(
    val name: Boolean,
    val nutrients: Set<Nutrient>,
) {
    val hasAny: Boolean get() = name || nutrients.isNotEmpty()
}

/** Formats with the decimal separator of [locale] and without trailing zeros, e.g. 0.40 -> "0,4" (German). */
fun formatDecimal(value: Double, locale: Locale = Locale.getDefault()): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        .replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)

internal fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

internal fun String.toPositiveDecimalOrNull(): Double? = toDecimalOrNull()?.takeIf { it > 0 }
