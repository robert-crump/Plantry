package com.example.plantry.ui.recipe

import com.example.plantry.data.Ingredient
import com.example.plantry.data.RecipeIngredientDraft
import com.example.plantry.ui.ingredient.formatDecimal
import com.example.plantry.ui.ingredient.toPositiveDecimalOrNull

/**
 * Raw input of the ingredient line editor. Typing into the ingredient field clears the selected
 * ingredient until the user picks one of the suggestions.
 */
data class RecipeLineForm(
    val originalText: String = "",
    val grams: String = "",
    val ingredientQuery: String = "",
    val ingredientId: Long? = null,
) {
    fun withIngredientQuery(query: String) = copy(ingredientQuery = query, ingredientId = null)

    fun withIngredient(ingredient: Ingredient) = copy(ingredientQuery = ingredient.name, ingredientId = ingredient.id)

    fun errors() = RecipeLineFormErrors(
        grams = grams.toPositiveDecimalOrNull() == null,
        ingredient = ingredientId == null,
    )

    /**
     * Returns the validated line, or null if any field is invalid. A blank original text defaults
     * to the amount and ingredient, e.g. "300 g Süßkartoffel".
     */
    fun toDraft(): RecipeIngredientDraft? {
        if (errors().hasAny) return null
        val grams = grams.toPositiveDecimalOrNull()!!
        return RecipeIngredientDraft(
            originalText = originalText.trim().ifEmpty { "${formatDecimal(grams)} g ${ingredientQuery.trim()}" },
            grams = grams,
            ingredientId = ingredientId!!,
        )
    }

    companion object {
        fun from(line: RecipeIngredientDraft, ingredientName: String) = RecipeLineForm(
            originalText = line.originalText,
            grams = formatDecimal(line.grams),
            ingredientQuery = ingredientName,
            ingredientId = line.ingredientId,
        )
    }
}

data class RecipeLineFormErrors(val grams: Boolean, val ingredient: Boolean) {
    val hasAny: Boolean get() = grams || ingredient
}
