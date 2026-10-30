package com.example.plantry.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val source: String,
    val page: Int?,
    /** "Portionen", what nutrition per portion divides by; for a snack e.g. 20 rice cakes. */
    val servings: Int,
    /** "Gesamtzeit", until the food is ready; null when the recipe states no time, as most handwritten ones. */
    @ColumnInfo(name = "cookingTimeMinutes") val totalTimeMinutes: Int?,
    /** Set once the ingredient lines were edited after the recipe was first saved. */
    @ColumnInfo(defaultValue = "0") val modified: Boolean = false,
    /** Only main dishes are suggested; existing recipes became main dishes. */
    @ColumnInfo(defaultValue = "MAIN") val dish: Dish = Dish.MAIN,
    /** "Arbeitszeit", the hands-on part of [totalTimeMinutes]; null when unknown, as for all older recipes. */
    val activeTimeMinutes: Int? = null,
    /** "Zubereitung", see [CookingSteps]; empty when the recipe has none, as all older recipes. */
    @ColumnInfo(defaultValue = "") @field:TypeConverters(CookingSteps.Converter::class) val steps: List<String> = emptyList(),
)

/** What a recipe is eaten as: "Hauptgericht", "Frühstück" or "Snack"; stored by name. */
enum class Dish { MAIN, BREAKFAST, SNACK }

/** User input for creating or updating a [Recipe]. */
data class RecipeDraft(
    val title: String,
    val source: String,
    val page: Int?,
    val servings: Int,
    val totalTimeMinutes: Int?,
    val lines: List<RecipeIngredientDraft>,
    val dish: Dish = Dish.MAIN,
    val activeTimeMinutes: Int? = null,
    val steps: List<String> = emptyList(),
)
