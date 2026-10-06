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
)

enum class StoreSection { PRODUCE, DAIRY_CHILLED, DRY_GOODS, FROZEN, OTHER }

enum class PlantPoints(val value: Double) { ONE(1.0), QUARTER(0.25), ZERO(0.0) }

/** User-editable attributes of an [Ingredient]; the USDA reference is kept as is. */
data class IngredientDraft(
    val name: String,
    val nutrition: Nutrition,
    val storeSection: StoreSection,
    val plantPoints: PlantPoints,
)
