package com.example.plantry.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "ingredients",
    foreignKeys = [
        ForeignKey(
            entity = Ingredient::class,
            parentColumns = ["id"],
            childColumns = ["buyAsIngredientId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("buyAsIngredientId")],
)
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** German display name. */
    val name: String,
    /** USDA FoodData Central entry the nutrition was taken from, if any. */
    val fdcId: Long?,
    val usdaDescription: String?,
    /** Per 100 g. */
    @Embedded val nutrition: Nutrition,
    val unitWeights: List<UnitWeight>,
    val buyUnit: BuyUnit,
    /** Only set when [buyUnit] is [BuyUnit.PACK]. */
    val packSizeGrams: Double?,
    val storeSection: StoreSection,
    val staple: Boolean,
    val plantPoints: PlantPoints,
    /** The ingredient this one is bought as, e.g. "rice, cooked" -> "rice, dry". */
    val buyAsIngredientId: Long?,
    /** Grams of the buy-as ingredient per gram of this one, e.g. 0.4 for cooked -> dry rice. */
    val buyAsYieldFactor: Double?,
    val reviewed: Boolean,
) {
    /** Weight of one piece, used when buying by [BuyUnit.PIECES]. */
    val pieceWeightGrams: Double? get() = unitWeights.pieceWeight()
}

@Serializable
data class UnitWeight(val label: String, val grams: Double)

/** The "medium" (or German "mittel") unit weight defines the weight of one piece. */
fun List<UnitWeight>.pieceWeight(): Double? = firstOrNull { unit ->
    val label = unit.label.trim().lowercase()
    label.startsWith("medium") || label.startsWith("mittel")
}?.grams

enum class BuyUnit { PIECES, PACK, GRAMS }

enum class StoreSection { PRODUCE, DAIRY_CHILLED, DRY_GOODS, FROZEN, OTHER }

enum class PlantPoints(val value: Double) { ONE(1.0), QUARTER(0.25), ZERO(0.0) }

/** User-editable attributes of an [Ingredient]; the USDA reference is kept as is. */
data class IngredientDraft(
    val name: String,
    val nutrition: Nutrition,
    val unitWeights: List<UnitWeight>,
    val buyUnit: BuyUnit,
    val packSizeGrams: Double?,
    val storeSection: StoreSection,
    val staple: Boolean,
    val plantPoints: PlantPoints,
    val buyAsIngredientId: Long?,
    val buyAsYieldFactor: Double?,
)

/** The buy-as link of one ingredient, used for cycle detection. */
data class BuyAsLink(val id: Long, val buyAsIngredientId: Long?)
