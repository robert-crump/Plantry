package com.example.plantry.ui.ingredient

import com.example.plantry.data.Nutrient
import com.example.plantry.data.Nutrition

/**
 * The values per 100 g from a package label, for an ingredient without a USDA entry. Every
 * nutrient is required (0 is fine), so a missing value never silently counts as 0.
 */
data class LabelNutritionForm(
    val name: String = "",
    val values: Map<Nutrient, String> = Nutrient.entries.associateWith { "" },
) {
    fun withValue(nutrient: Nutrient, value: String) = copy(values = values + (nutrient to value))

    /** Nutrients with a value that is not a number ≥ 0; blank ones are missing, not wrong. */
    fun invalid(): Set<Nutrient> = Nutrient.entries.filterTo(mutableSetOf()) { nutrient ->
        val value = values[nutrient].orEmpty()
        value.isNotBlank() && value.toDecimalOrNull().let { it == null || it < 0 }
    }

    /** All values as [Nutrition], or null while any is blank or invalid. */
    fun toNutrition(): Nutrition? {
        val parsed = Nutrient.entries.associateWith { values[it].orEmpty().toDecimalOrNull() }
        if (parsed.values.any { it == null || it < 0 }) return null
        return Nutrition.of(parsed.mapValues { it.value!! })
    }

    companion object {
        fun from(nutrition: Nutrition) = LabelNutritionForm(values = Nutrient.entries.associateWith { formatDecimal(nutrition[it]) })
    }
}
