package com.example.plantry.ui.ingredient

import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientDraft
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.LabelSource
import com.example.plantry.data.Nutrient
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PieceUnit
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.StoreSection
import com.example.plantry.data.openfoodfacts.OffProduct
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Raw input of the ingredient detail screen. Decimals accept both "," and ".". */
data class IngredientForm(
    val name: String = "",
    val nutrition: Map<Nutrient, String> = Nutrient.entries.associateWith { "" },
    val storeSection: StoreSection = StoreSection.OTHER,
    val plantPoints: PlantPoints = PlantPoints.ZERO,
    /** One row per unit in [PieceUnit] order, each already validated by [UnitForm] in its dialog. */
    val units: List<IngredientUnit> = emptyList(),
    /** The package applied from a barcode scan; replaces the USDA reference on save. */
    val scannedLabel: LabelSource? = null,
) {
    /** The units a row can still be given: those without a row, plus [editing]'s own. */
    fun availableUnits(editing: PieceUnit? = null): List<PieceUnit> =
        PieceUnit.entries.filter { unit -> unit == editing || units.none { it.unit == unit } }

    /** Puts [unit] in place of the row of [replacing] (null when adding), or of a row with the same unit. */
    fun withUnit(unit: IngredientUnit, replacing: PieceUnit? = null) =
        copy(units = (units.filter { it.unit != replacing && it.unit != unit.unit } + unit).sortedBy { it.unit })

    fun withoutUnit(unit: PieceUnit) = copy(units = units.filter { it.unit != unit })

    fun withNutrient(nutrient: Nutrient, value: String) = copy(nutrition = nutrition + (nutrient to value))

    /** Takes the values [product] has and keeps the others; the name stays as it is. */
    fun withScanned(product: OffProduct) = copy(
        nutrition = nutrition + product.nutrition.mapValues { formatDecimal(it.value) },
        scannedLabel = product.labelSource,
    )

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
            units = units,
            scannedLabel = scannedLabel,
        )
    }

    companion object {
        fun from(ingredient: Ingredient, units: List<IngredientUnit> = emptyList()) = IngredientForm(
            name = ingredient.name,
            nutrition = Nutrient.entries.associateWith { formatDecimal(ingredient.nutrition[it]) },
            storeSection = ingredient.storeSection,
            plantPoints = ingredient.plantPoints,
            units = units.sortedBy { it.unit },
        )
    }
}

/**
 * Raw input of the dialog for one unit row: [grams] is the weight of one [unit], or of a package
 * holding [count] of them when [inPackage] ("750 g ÷ 20").
 */
data class UnitForm(
    val unit: PieceUnit? = null,
    val inPackage: Boolean = false,
    val grams: String = "",
    val count: String = "",
) {
    val gramsInvalid: Boolean get() = grams.toPositiveDecimalOrNull() == null

    val countInvalid: Boolean get() = inPackage && count.toPositiveWholeOrNull() == null

    val isValid: Boolean get() = unit != null && !gramsInvalid && !countInvalid

    /** The row, or null while invalid. */
    fun toUnit(): IngredientUnit? {
        val unit = unit ?: return null
        val grams = grams.toPositiveDecimalOrNull() ?: return null
        return if (inPackage) {
            IngredientUnit.ofPackage(unit, grams, count.toPositiveWholeOrNull() ?: return null)
        } else {
            IngredientUnit.perUnit(unit, grams)
        }
    }

    companion object {
        /** Reopens [row] in the mode it was entered in; a new row starts per unit. */
        fun from(row: IngredientUnit?) = when {
            row == null -> UnitForm()
            row.isPackage -> UnitForm(row.unit, inPackage = true, formatDecimal(row.packageGrams!!), row.packageCount.toString())
            else -> UnitForm(row.unit, inPackage = false, formatDecimal(row.gramsPerUnit))
        }
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

private fun String.toPositiveWholeOrNull(): Int? = trim().toIntOrNull()?.takeIf { it > 0 }
