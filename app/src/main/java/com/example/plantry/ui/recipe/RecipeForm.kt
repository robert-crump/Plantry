package com.example.plantry.ui.recipe

import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeDraft

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
) {
    fun withBookServings(value: String) = copy(
        bookServings = value,
        ourServings = if (ourServingsEdited) ourServings else value,
    )

    fun withOurServings(value: String) = copy(ourServings = value, ourServingsEdited = true)

    fun errors() = RecipeFormErrors(
        title = title.isBlank(),
        page = page.isNotBlank() && page.toPositiveIntOrNull() == null,
        bookServings = bookServings.toPositiveIntOrNull() == null,
        ourServings = ourServings.isNotBlank() && ourServings.toPositiveIntOrNull() == null,
        cookingTime = cookingTime.toPositiveIntOrNull() == null,
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
        )
    }

    companion object {
        fun from(recipe: Recipe) = RecipeForm(
            title = recipe.title,
            source = recipe.source,
            page = recipe.page?.toString().orEmpty(),
            bookServings = recipe.bookServings.toString(),
            ourServings = recipe.ourServings.toString(),
            cookingTime = recipe.cookingTimeMinutes.toString(),
            ourServingsEdited = true,
        )
    }
}

data class RecipeFormErrors(
    val title: Boolean,
    val page: Boolean,
    val bookServings: Boolean,
    val ourServings: Boolean,
    val cookingTime: Boolean,
) {
    val hasAny: Boolean get() = title || page || bookServings || ourServings || cookingTime
}

private fun String.toPositiveIntOrNull(): Int? = trim().toIntOrNull()?.takeIf { it > 0 }
