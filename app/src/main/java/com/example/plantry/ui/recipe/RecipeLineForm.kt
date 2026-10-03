package com.example.plantry.ui.recipe

import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientAliases
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
    /**
     * Claude's name for the food of a scanned line, which applying the line confirms as an alias of
     * the chosen ingredient; blank for a line entered by hand.
     */
    val scannedName: String = "",
) {
    fun withIngredientQuery(query: String) = copy(ingredientQuery = query, ingredientId = null)

    /**
     * Selects the ingredient the typed text is a learned alias of, if none is selected yet; [names]
     * by ingredient id.
     */
    fun withAlias(aliases: Map<String, Long>, names: Map<Long, String>): RecipeLineForm {
        if (ingredientId != null) return this
        val id = IngredientAliases.match(ingredientQuery, aliases) ?: return this
        return withIngredient(id, names[id] ?: return this)
    }

    fun withIngredient(ingredient: Ingredient) = withIngredient(ingredient.id, ingredient.name)

    /** A negative [id] is a new ingredient proposed by Claude, see [RecipeForm.newIngredients]. */
    fun withIngredient(id: Long, name: String) = copy(ingredientQuery = name, ingredientId = id)

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

    /** The validated line for the recipe form, marked as confirmed by the user. */
    fun toFormLine(): RecipeFormLine? = toDraft()?.let {
        RecipeFormLine(it.originalText, it.grams, it.ingredientId, ingredientName = scannedName, confirmed = true)
    }

    companion object {
        fun from(line: RecipeIngredientDraft, ingredientName: String) = from(RecipeFormLine.from(line), ingredientName)

        /** An unmatched line starts with Claude's name for the food as the search term. */
        fun from(line: RecipeFormLine, ingredientName: String?) = RecipeLineForm(
            originalText = line.originalText,
            grams = if (line.grams > 0.0) formatDecimal(line.grams) else "",
            ingredientQuery = ingredientName ?: line.ingredientName,
            ingredientId = line.ingredientId,
            scannedName = line.ingredientName,
        )
    }
}

data class RecipeLineFormErrors(val grams: Boolean, val ingredient: Boolean) {
    val hasAny: Boolean get() = grams || ingredient
}
