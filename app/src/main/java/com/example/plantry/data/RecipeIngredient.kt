package com.example.plantry.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One ingredient line of a recipe, e.g. "1 große Süßkartoffel" = 300 g Süßkartoffel. */
@Entity(
    tableName = "recipe_ingredients",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Ingredient::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("recipeId"), Index("ingredientId")],
)
data class RecipeIngredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    /** Order of the line within the recipe, starting at 0. */
    val position: Int,
    /** Display wording as written in the book. */
    val originalText: String,
    val grams: Double,
    val ingredientId: Long,
)

/** User input for one ingredient line; its position is its index in the recipe's list. */
data class RecipeIngredientDraft(
    val originalText: String,
    val grams: Double,
    val ingredientId: Long,
)

fun RecipeIngredient.toDraft() = RecipeIngredientDraft(originalText, grams, ingredientId)

fun List<RecipeIngredientDraft>.toEntities(recipeId: Long) = mapIndexed { index, line ->
    RecipeIngredient(
        recipeId = recipeId,
        position = index,
        originalText = line.originalText.trim(),
        grams = line.grams,
        ingredientId = line.ingredientId,
    )
}
