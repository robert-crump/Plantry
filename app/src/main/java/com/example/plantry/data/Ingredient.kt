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
    /** The scanned package the nutrition was taken from, instead of USDA; see [LabelSource]. */
    @Embedded val labelSource: LabelSource? = null,
)

/**
 * A package whose barcode was looked up in Open Food Facts for the nutrition. Only shown as the
 * source; the barcode is no lookup key, since one ingredient stands for many brands.
 */
data class LabelSource(val labelProduct: String, val labelBarcode: String)

/** The units an ingredient can be counted in, with their German words for one and for several. */
enum class PieceUnit(val singular: String, val plural: String) {
    PIECE("Stück", "Stück"),
    SLICE("Scheibe", "Scheiben"),
    CLOVE("Zehe", "Zehen"),
    BUNCH("Bund", "Bund"),
    CAN("Dose", "Dosen"),
    PACKET("Packung", "Packungen"),
    TSP("TL", "TL"),
    TBSP("EL", "EL"),
    CUP("Tasse", "Tassen"),
}

enum class StoreSection { PRODUCE, DAIRY_CHILLED, DRY_GOODS, FROZEN, OTHER }

enum class PlantPoints(val value: Double) { ONE(1.0), QUARTER(0.25), ZERO(0.0) }

/**
 * What a new ingredient starts from: its name and where the nutrition came from. The ingredient is
 * only created once the user saves it, see [IngredientRepository.create].
 */
data class NewIngredientSeed(
    val name: String,
    val nutrition: Nutrition,
    val fdcId: Long? = null,
    val usdaDescription: String? = null,
    val labelSource: LabelSource? = null,
) {
    /** The unsaved ingredient the detail screen shows, with the default store section and plant points. */
    fun toIngredient() = Ingredient(
        name = name,
        fdcId = fdcId,
        usdaDescription = usdaDescription,
        nutrition = nutrition,
        storeSection = StoreSection.OTHER,
        plantPoints = PlantPoints.ZERO,
        labelSource = labelSource,
    )
}

/** User-editable attributes of an [Ingredient]; the USDA reference is kept unless [scannedLabel] is set. */
data class IngredientDraft(
    val name: String,
    val nutrition: Nutrition,
    val storeSection: StoreSection,
    val plantPoints: PlantPoints,
    /** At most one row per unit; their ingredient id is ignored. */
    val units: List<IngredientUnit> = emptyList(),
    /** Set after nutrition was taken from a scanned package: replaces the USDA reference on save. */
    val scannedLabel: LabelSource? = null,
)
