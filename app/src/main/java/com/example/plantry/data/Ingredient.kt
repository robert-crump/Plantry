package com.example.plantry.data

import androidx.annotation.Keep
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.plantry.data.claude.IngredientProposal
import com.example.plantry.data.openfoodfacts.OffProduct
import com.example.plantry.data.usda.UsdaFood

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
    STALK("Stange", "Stangen"),
    CAN("Dose", "Dosen"),
    PACKET("Packung", "Packungen"),
    TSP("TL", "TL"),
    TBSP("EL", "EL"),
    CUP("Tasse", "Tassen"),
}

/** Kept: a navigation route carries it, see IngredientCreateRoute. */
@Keep
enum class StoreSection { PRODUCE, DAIRY_CHILLED, DRY_GOODS, FROZEN, OTHER }

@Keep
enum class PlantPoints(val value: Double) { ONE(1.0), QUARTER(0.25), ZERO(0.0) }

/**
 * What a new ingredient starts from: its name, where the nutrition came from, and the other values
 * pre-filled on its screen. The ingredient is only created once the user saves it, see
 * [IngredientRepository.create].
 */
data class NewIngredientSeed(
    val name: String,
    val nutrition: Nutrition,
    val fdcId: Long? = null,
    val usdaDescription: String? = null,
    val labelSource: LabelSource? = null,
    val storeSection: StoreSection = StoreSection.OTHER,
    val plantPoints: PlantPoints = PlantPoints.ZERO,
    /** One row per unit; their ingredient id is ignored. */
    val units: List<IngredientUnit> = emptyList(),
) {
    /** The unsaved ingredient the detail screen shows. */
    fun toIngredient() = Ingredient(
        name = name,
        fdcId = fdcId,
        usdaDescription = usdaDescription,
        nutrition = nutrition,
        storeSection = storeSection,
        plantPoints = plantPoints,
        labelSource = labelSource,
    )

    companion object {
        /**
         * A scanned package with the [name] and [nutrition] the user confirmed; a known package
         * size becomes a Packung unit, which the units dialog also offers for "750 g ÷ 20".
         */
        fun fromProduct(product: OffProduct, name: String, nutrition: Nutrition) = NewIngredientSeed(
            name = name,
            nutrition = nutrition,
            labelSource = product.labelSource,
            units = listOfNotNull(product.packageGrams?.let { IngredientUnit.perUnit(PieceUnit.PACKET, it) }),
        )

        /**
         * Claude's proposal; [labelNutrition] (with the user's [name]) stands in for a missing USDA
         * entry. Requires one of the two.
         */
        fun fromProposal(proposal: IngredientProposal, name: String = proposal.name, labelNutrition: Nutrition? = null): NewIngredientSeed {
            val food = proposal.food.takeIf { labelNutrition == null }
            return NewIngredientSeed(
                name = name,
                nutrition = labelNutrition ?: checkNotNull(proposal.source) { "a new ingredient needs a nutrition source" }.nutrition,
                fdcId = food?.fdcId,
                usdaDescription = food?.description,
                storeSection = proposal.storeSection,
                plantPoints = proposal.plantPoints,
                units = proposal.units(),
            )
        }
    }
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
    /** Set after nutrition was taken from a picked USDA entry: replaces the source on save. */
    val pickedUsda: UsdaFood? = null,
    /** What the ingredient contains ("Enthält"); replaces the stored components on save. */
    val components: List<MixPart> = emptyList(),
)

/** A component of a mix: an existing ingredient, or (with [id] null) one created by [name] when the mix is saved. */
data class MixPart(val id: Long?, val name: String)
