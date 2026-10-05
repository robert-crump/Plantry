package com.example.plantry.ui.ingredient

import com.example.plantry.data.BuyUnit
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientDraft
import com.example.plantry.data.Nutrient
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import com.example.plantry.data.UnitWeight
import com.example.plantry.data.pieceWeight
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

data class UnitWeightInput(val label: String = "", val grams: String = "")

/** Raw input of the ingredient detail screen. Decimals accept both "," and ".". */
data class IngredientForm(
    val name: String = "",
    val nutrition: Map<Nutrient, String> = Nutrient.entries.associateWith { "" },
    val unitWeights: List<UnitWeightInput> = emptyList(),
    val buyUnit: BuyUnit = BuyUnit.GRAMS,
    val packSize: String = "",
    val storeSection: StoreSection = StoreSection.OTHER,
    val staple: Boolean = false,
    val plantPoints: PlantPoints = PlantPoints.ZERO,
    val buyAsIngredientId: Long? = null,
    val buyAsYieldFactor: String = "",
) {
    fun withNutrient(nutrient: Nutrient, value: String) = copy(nutrition = nutrition + (nutrient to value))

    fun withUnitWeight(index: Int, unit: UnitWeightInput) =
        copy(unitWeights = unitWeights.toMutableList().also { it[index] = unit })

    fun addUnitWeight() = copy(unitWeights = unitWeights + UnitWeightInput())

    fun removeUnitWeight(index: Int) = copy(unitWeights = unitWeights.filterIndexed { i, _ -> i != index })

    fun errors(): IngredientFormErrors {
        val units = parsedUnitWeights()
        return IngredientFormErrors(
            name = name.isBlank(),
            nutrients = Nutrient.entries.filterTo(mutableSetOf()) { nutrient ->
                nutrition[nutrient].orEmpty().toDecimalOrNull().let { it == null || it < 0 }
            },
            unitWeights = unitWeights.indices.filterTo(mutableSetOf()) { units[it] == null },
            packSize = buyUnit == BuyUnit.PACK && packSize.toPositiveDecimalOrNull() == null,
            pieceWeightMissing = buyUnit == BuyUnit.PIECES && units.filterNotNull().pieceWeight() == null,
            buyAsYieldFactor = buyAsIngredientId != null && buyAsYieldFactor.toPositiveDecimalOrNull() == null,
        )
    }

    /** Returns the validated draft, or null if any field is invalid. */
    fun toDraft(): IngredientDraft? {
        if (errors().hasAny) return null
        return IngredientDraft(
            name = name,
            nutrition = Nutrition.of(nutrition.mapValues { it.value.toDecimalOrNull()!! }),
            unitWeights = parsedUnitWeights().filterNotNull(),
            buyUnit = buyUnit,
            packSizeGrams = packSize.toPositiveDecimalOrNull().takeIf { buyUnit == BuyUnit.PACK },
            storeSection = storeSection,
            staple = staple,
            plantPoints = plantPoints,
            buyAsIngredientId = buyAsIngredientId,
            buyAsYieldFactor = buyAsYieldFactor.toPositiveDecimalOrNull().takeIf { buyAsIngredientId != null },
        )
    }

    private fun parsedUnitWeights(): List<UnitWeight?> = unitWeights.map { input ->
        val grams = input.grams.toPositiveDecimalOrNull()
        if (input.label.isBlank() || grams == null) null else UnitWeight(input.label.trim(), grams)
    }

    companion object {
        fun from(ingredient: Ingredient) = IngredientForm(
            name = ingredient.name,
            nutrition = Nutrient.entries.associateWith { formatDecimal(ingredient.nutrition[it]) },
            unitWeights = ingredient.unitWeights.map { UnitWeightInput(it.label, formatDecimal(it.grams)) },
            buyUnit = ingredient.buyUnit,
            packSize = ingredient.packSizeGrams?.let(::formatDecimal).orEmpty(),
            storeSection = ingredient.storeSection,
            staple = ingredient.staple,
            plantPoints = ingredient.plantPoints,
            buyAsIngredientId = ingredient.buyAsIngredientId,
            buyAsYieldFactor = ingredient.buyAsYieldFactor?.let(::formatDecimal).orEmpty(),
        )
    }
}

data class IngredientFormErrors(
    val name: Boolean,
    val nutrients: Set<Nutrient>,
    /** Indices of unit weight rows with a blank label or non-positive grams. */
    val unitWeights: Set<Int>,
    val packSize: Boolean,
    /** Buying by pieces needs a "medium" unit weight. */
    val pieceWeightMissing: Boolean,
    val buyAsYieldFactor: Boolean,
) {
    val hasAny: Boolean
        get() = name || nutrients.isNotEmpty() || unitWeights.isNotEmpty() || packSize ||
            pieceWeightMissing || buyAsYieldFactor
}

/** Formats with the decimal separator of [locale] and without trailing zeros, e.g. 0.40 -> "0,4" (German). */
fun formatDecimal(value: Double, locale: Locale = Locale.getDefault()): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        .replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)

internal fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

internal fun String.toPositiveDecimalOrNull(): Double? = toDecimalOrNull()?.takeIf { it > 0 }
