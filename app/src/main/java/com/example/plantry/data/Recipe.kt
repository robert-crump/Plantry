package com.example.plantry.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val source: String,
    val page: Int?,
    val bookServings: Int,
    val ourServings: Int,
    /** Null when the recipe states no time, as most handwritten ones. */
    val cookingTimeMinutes: Int?,
    /** Set once the ingredient lines were edited after the recipe was first saved. */
    @ColumnInfo(defaultValue = "0") val modified: Boolean = false,
    /** Only main dishes are suggested; existing recipes became main dishes. */
    @ColumnInfo(defaultValue = "MAIN") val dish: Dish = Dish.MAIN,
)

/** What a recipe is eaten as: "Hauptgericht" or "Snack". */
enum class Dish { MAIN, SNACK }

/** User input for creating or updating a [Recipe]; [ourServings] falls back to [bookServings]. */
data class RecipeDraft(
    val title: String,
    val source: String,
    val page: Int?,
    val bookServings: Int,
    val ourServings: Int?,
    val cookingTimeMinutes: Int?,
    val lines: List<RecipeIngredientDraft>,
    val dish: Dish = Dish.MAIN,
)
