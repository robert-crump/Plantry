package com.example.plantry.ui.recipe

import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeDraft
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.RecipeIngredientDraft
import com.example.plantry.data.claude.ScannedRecipe
import com.example.plantry.data.toDraft

/**
 * Raw text input of the recipe edit screen. Until the user touches "our servings" it mirrors
 * "book servings", so a new recipe defaults to the book's serving count.
 */
data class RecipeForm(
    val title: String = "",
    val source: String = "",
    val page: String = "",
    val bookServings: String = "",
    val ourServings: String = "",
    val cookingTime: String = "",
    val ourServingsEdited: Boolean = false,
    /** Ingredient lines are validated in the line editor, so they are kept parsed. */
    val lines: List<RecipeFormLine> = emptyList(),
) {
    fun withBookServings(value: String) = copy(
        bookServings = value,
        ourServings = if (ourServingsEdited) ourServings else value,
    )

    fun withOurServings(value: String) = copy(ourServings = value, ourServingsEdited = true)

    /** Replaces the line at [index], or appends [line] when [index] is null. */
    fun withLine(index: Int?, line: RecipeIngredientDraft) = withLine(index, RecipeFormLine.from(line))

    fun withLine(index: Int?, line: RecipeFormLine) =
        copy(lines = if (index == null) lines + line else lines.toMutableList().also { it[index] = line })

    /** Takes what Claude read; the source is kept, since a page photo rarely shows the book. */
    fun withScan(scan: ScannedRecipe) = RecipeForm(
        title = scan.title,
        source = source,
        page = scan.page?.toString().orEmpty(),
        bookServings = scan.servings?.toString().orEmpty(),
        ourServings = scan.servings?.toString().orEmpty(),
        cookingTime = scan.cookingTimeMinutes?.toString().orEmpty(),
        lines = scan.lines.map { RecipeFormLine(it.originalText, it.grams, it.ingredientId, it.ingredientName, it.uncertain) },
    )

    /** The lines that are complete enough for nutrition. */
    fun completeLines(): List<RecipeIngredientDraft> = lines.mapNotNull { it.toDraft() }

    fun removeLine(index: Int) = copy(lines = lines.filterIndexed { i, _ -> i != index })

    /** The servings nutrition is divided by: our servings, else book servings, else null. */
    fun effectiveServings(): Int? = ourServings.toPositiveIntOrNull() ?: bookServings.toPositiveIntOrNull()

    fun errors() = RecipeFormErrors(
        title = title.isBlank(),
        page = page.isNotBlank() && page.toPositiveIntOrNull() == null,
        bookServings = bookServings.toPositiveIntOrNull() == null,
        ourServings = ourServings.isNotBlank() && ourServings.toPositiveIntOrNull() == null,
        cookingTime = cookingTime.toPositiveIntOrNull() == null,
        lines = lines.any { it.toDraft() == null },
    )

    /** Returns the validated draft, or null if any field is invalid. */
    fun toDraft(): RecipeDraft? {
        if (errors().hasAny) return null
        return RecipeDraft(
            title = title,
            source = source,
            page = page.toPositiveIntOrNull(),
            bookServings = bookServings.toPositiveIntOrNull()!!,
            ourServings = ourServings.toPositiveIntOrNull(),
            cookingTimeMinutes = cookingTime.toPositiveIntOrNull()!!,
            lines = completeLines(),
        )
    }

    companion object {
        fun from(recipe: Recipe, lines: List<RecipeIngredient>) = RecipeForm(
            title = recipe.title,
            source = recipe.source,
            page = recipe.page?.toString().orEmpty(),
            bookServings = recipe.bookServings.toString(),
            ourServings = recipe.ourServings.toString(),
            cookingTime = recipe.cookingTimeMinutes.toString(),
            ourServingsEdited = true,
            lines = lines.map { RecipeFormLine.from(it.toDraft()) },
        )
    }
}

data class RecipeFormErrors(
    val title: Boolean,
    val page: Boolean,
    val bookServings: Boolean,
    val ourServings: Boolean,
    val cookingTime: Boolean,
    /** Some line has no ingredient or no weight yet. */
    val lines: Boolean = false,
) {
    val hasAny: Boolean get() = title || page || bookServings || ourServings || cookingTime || lines
}

/** An ingredient line as edited; a scanned line may still lack an ingredient or a weight. */
data class RecipeFormLine(
    val originalText: String,
    val grams: Double,
    val ingredientId: Long?,
    /** Claude's German name for the food, the search term while [ingredientId] is null. */
    val ingredientName: String = "",
    /** Claude was unsure about this line; cleared once the user edits it. */
    val uncertain: Boolean = false,
) {
    val complete: Boolean get() = ingredientId != null && grams > 0.0

    fun toDraft(): RecipeIngredientDraft? =
        if (complete) RecipeIngredientDraft(originalText, grams, ingredientId!!) else null

    companion object {
        fun from(line: RecipeIngredientDraft) = RecipeFormLine(line.originalText, line.grams, line.ingredientId)
    }
}

private fun String.toPositiveIntOrNull(): Int? = trim().toIntOrNull()?.takeIf { it > 0 }
