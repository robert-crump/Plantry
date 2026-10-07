package com.example.plantry.ui.ingredient

import com.example.plantry.data.DrainedWeight
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientDraft
import com.example.plantry.data.LabelSource
import com.example.plantry.data.Nutrient
import com.example.plantry.data.Nutrition
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
    /** Null while undecided, which is an error until the user chooses. */
    val storeSection: StoreSection? = StoreSection.OTHER,
    /** Null while undecided, like [storeSection]. */
    val plantPoints: PlantPoints? = PlantPoints.ZERO,
    /** Already validated by [DrainedWeightForm] in its dialog. */
    val drainedWeight: DrainedWeight? = null,
    /** False until the user said whether the product is drained, as null means "no" as well. */
    val drainedAnswered: Boolean = true,
    /** The package applied from a barcode scan; replaces the USDA reference on save. */
    val scannedLabel: LabelSource? = null,
) {
    fun withNutrient(nutrient: Nutrient, value: String) = copy(nutrition = nutrition + (nutrient to value))

    /** Null for "not drained"; either way the question is answered. */
    fun withDrainedWeight(weight: DrainedWeight?) = copy(drainedWeight = weight, drainedAnswered = true)

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
        storeSection = storeSection == null,
        plantPoints = plantPoints == null,
        drained = !drainedAnswered,
    )

    /** Returns the validated draft, or null if any field is invalid. */
    fun toDraft(): IngredientDraft? {
        if (errors().hasAny) return null
        return IngredientDraft(
            name = name,
            nutrition = Nutrition.of(nutrition.mapValues { it.value.toDecimalOrNull()!! }),
            storeSection = storeSection!!,
            plantPoints = plantPoints!!,
            drainedWeight = drainedWeight,
            scannedLabel = scannedLabel,
        )
    }

    companion object {
        /** [drainedAnswered] is false for an ingredient that doesn't exist yet. */
        fun from(ingredient: Ingredient, drainedAnswered: Boolean = true) = IngredientForm(
            name = ingredient.name,
            nutrition = Nutrient.entries.associateWith { formatDecimal(ingredient.nutrition[it]) },
            storeSection = ingredient.storeSection.takeUnless { ingredient.storeSectionUndecided },
            plantPoints = ingredient.plantPoints.takeUnless { ingredient.plantPointsUndecided },
            drainedWeight = ingredient.drainedWeight,
            drainedAnswered = drainedAnswered,
        )
    }
}

/** Raw input of the Abtropfgewicht dialog, in grams: both empty, or 0 < drained ≤ net. */
data class DrainedWeightForm(val net: String = "", val drained: String = "") {
    private val isEmpty: Boolean get() = net.isBlank() && drained.isBlank()

    val netInvalid: Boolean get() = !isEmpty && net.toPositiveDecimalOrNull() == null

    val drainedError: DrainedWeightError?
        get() {
            if (isEmpty) return null
            val drained = drained.toPositiveDecimalOrNull() ?: return DrainedWeightError.INVALID
            val net = net.toPositiveDecimalOrNull()
            return if (net != null && drained > net) DrainedWeightError.EXCEEDS_NET else null
        }

    val isValid: Boolean get() = !netInvalid && drainedError == null

    /** The weights, or null when both fields are empty (not a drained product) or invalid. */
    fun toDrainedWeight(): DrainedWeight? =
        if (isValid) DrainedWeight.of(net.toPositiveDecimalOrNull(), drained.toPositiveDecimalOrNull()) else null

    companion object {
        fun from(weight: DrainedWeight?) = DrainedWeightForm(
            net = weight?.let { formatDecimal(it.netWeightGrams) }.orEmpty(),
            drained = weight?.let { formatDecimal(it.drainedWeightGrams) }.orEmpty(),
        )
    }
}

enum class DrainedWeightError { INVALID, EXCEEDS_NET }

data class IngredientFormErrors(
    val name: Boolean,
    val nutrients: Set<Nutrient>,
    val storeSection: Boolean = false,
    val plantPoints: Boolean = false,
    /** Whether the product is drained was never answered. */
    val drained: Boolean = false,
) {
    val hasAny: Boolean get() = name || nutrients.isNotEmpty() || storeSection || plantPoints || drained
}

/** Formats with the decimal separator of [locale] and without trailing zeros, e.g. 0.40 -> "0,4" (German). */
fun formatDecimal(value: Double, locale: Locale = Locale.getDefault()): String =
    BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        .replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)

internal fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

internal fun String.toPositiveDecimalOrNull(): Double? = toDecimalOrNull()?.takeIf { it > 0 }
