package com.example.plantry.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One ingredient line of a recipe, e.g. 2 Scheiben Vollkorntoast. Its grams are never stored; reading
 * gives a [RecipeLine], whose grams follow the ingredient's units.
 */
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
    val amount: Double,
    val unit: LineUnit,
    val ingredientId: Long,
    /** The line as the book words it, e.g. "1 daumengroßes Stück Ingwer"; null shows the generated text. */
    val wording: String? = null,
)

/**
 * A stored line as read, with the grams per unit of its ingredient joined in, so its [grams] are
 * worked out from the ingredient's units as they are now.
 */
data class RecipeLine(
    val id: Long = 0,
    val recipeId: Long,
    val position: Int,
    val amount: Double,
    val unit: LineUnit,
    val ingredientId: Long,
    val wording: String? = null,
    /** Grams per [unit] of the ingredient; null for g and ml, and for a unit the ingredient lacks. */
    val unitGrams: Double? = null,
)

/** Null when the ingredient lacks the line's unit; see [LineAmount]. */
val RecipeLine.grams: Double? get() = LineAmount.grams(amount, unit, unitGrams)

/** Grams, calculation and text of the line, whose ingredient is called [ingredientName]. */
fun RecipeLine.resolve(ingredientName: String) = LineAmount.resolve(amount, unit, unitGrams, ingredientName, wording)

/** User input for one ingredient line; its position is its index in the recipe's list. */
data class RecipeIngredientDraft(
    val amount: Double,
    val unit: LineUnit,
    val ingredientId: Long,
    val wording: String? = null,
)

fun RecipeLine.toDraft() = RecipeIngredientDraft(amount, unit, ingredientId, wording)

/** A blank wording is none. */
fun RecipeIngredientDraft.normalized() = copy(wording = wording?.trim()?.takeIf { it.isNotEmpty() })

fun List<RecipeIngredientDraft>.toEntities(recipeId: Long) = mapIndexed { index, draft ->
    val line = draft.normalized()
    RecipeIngredient(
        recipeId = recipeId,
        position = index,
        amount = line.amount,
        unit = line.unit,
        ingredientId = line.ingredientId,
        wording = line.wording,
    )
}
