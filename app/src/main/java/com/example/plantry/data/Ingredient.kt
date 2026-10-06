package com.example.plantry.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ingredients")
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** German display name. */
    val name: String,
    /** USDA FoodData Central entry the nutrition was taken from, if any. */
    val fdcId: Long?,
    val usdaDescription: String?,
    /** Per 100 g. */
    @Embedded val nutrition: Nutrition,
    val storeSection: StoreSection,
    val plantPoints: PlantPoints,
    val reviewed: Boolean,
    /** Set for canned or jarred goods that are drained; [nutrition] is then per 100 g drained. */
    @Embedded val drainedWeight: DrainedWeight? = null,
    /** The scanned package the nutrition was taken from, instead of USDA; see [LabelSource]. */
    @Embedded val labelSource: LabelSource? = null,
)

/**
 * A package whose barcode was looked up in Open Food Facts for the nutrition. Only shown as the
 * source; the barcode is no lookup key, since one ingredient stands for many brands.
 */
data class LabelSource(val labelProduct: String, val labelBarcode: String)

/**
 * The weights printed on a can or jar of a drained product, e.g. 400 g net, 240 g drained. Recipe
 * lines hold the net weight; only the drained solids count for nutrition, the liquid is negligible.
 */
data class DrainedWeight(val netWeightGrams: Double, val drainedWeightGrams: Double) {
    /** The share of a line's grams that counts for nutrition. */
    val share: Double get() = drainedWeightGrams / netWeightGrams

    companion object {
        /** Null unless 0 < [drained] ≤ [net]. */
        fun of(net: Double?, drained: Double?): DrainedWeight? =
            if (net != null && drained != null && drained > 0 && drained <= net) DrainedWeight(net, drained) else null
    }
}

/** The share of a recipe line's grams that counts for nutrition: 1 unless the ingredient is drained. */
val Ingredient.drainedShare: Double get() = drainedWeight?.share ?: 1.0

enum class StoreSection { PRODUCE, DAIRY_CHILLED, DRY_GOODS, FROZEN, OTHER }

enum class PlantPoints(val value: Double) { ONE(1.0), QUARTER(0.25), ZERO(0.0) }

/** User-editable attributes of an [Ingredient]; the USDA reference is kept unless [scannedLabel] is set. */
data class IngredientDraft(
    val name: String,
    val nutrition: Nutrition,
    val storeSection: StoreSection,
    val plantPoints: PlantPoints,
    val drainedWeight: DrainedWeight? = null,
    /** Set after nutrition was taken from a scanned package: replaces the USDA reference on save. */
    val scannedLabel: LabelSource? = null,
)
