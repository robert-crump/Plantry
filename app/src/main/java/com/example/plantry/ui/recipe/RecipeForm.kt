package com.example.plantry.ui.recipe

import com.example.plantry.data.BookPage
import com.example.plantry.data.BookSession
import com.example.plantry.data.Ingredient
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeDraft
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.RecipeIngredientDraft
import com.example.plantry.data.claude.IngredientProposal
import com.example.plantry.data.claude.NewFood
import com.example.plantry.data.claude.ProposalParser
import com.example.plantry.data.claude.ScannedRecipe
import com.example.plantry.data.toDraft
import com.example.plantry.data.usda.UsdaFood

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
    /**
     * Ingredients Claude proposed, by temporary negative id; lines refer to them by that id. Those
     * a line uses are created on save.
     */
    val newIngredients: Map<Long, NewIngredient> = emptyMap(),
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

    /**
     * Takes what Claude read, with source and page from [book] (see [BookSession.defaults]); by
     * default the source is kept, since a page photo rarely shows the book.
     */
    fun withScan(scan: ScannedRecipe, book: BookPage = BookPage(source, scan.page)) = RecipeForm(
        title = scan.title,
        source = book.source,
        page = book.page?.toString().orEmpty(),
        bookServings = scan.servings?.toString().orEmpty(),
        ourServings = scan.servings?.toString().orEmpty(),
        cookingTime = scan.cookingTimeMinutes?.toString().orEmpty(),
        lines = scan.lines.map {
            RecipeFormLine(it.originalText, it.grams, it.ingredientId, it.ingredientName, it.uncertain, it.searchTerms)
        },
    )

    /**
     * The foods of lines without an ingredient, one per distinct name (several lines may use the
     * same food), each with a fresh id from [newId].
     */
    fun unmatchedFoods(newId: () -> Long): List<NewFood> = lines
        .filter { it.ingredientId == null && it.ingredientName.isNotBlank() }
        .groupBy { it.ingredientName.normalizedName() }
        .values
        .map { group ->
            NewFood(
                id = newId(),
                name = group.first().ingredientName.trim(),
                originalText = group.first().originalText,
                searchTerms = ProposalParser.cleanTerms(group.flatMap { it.searchTerms }),
            )
        }

    /** Adds the proposals for [foods] and assigns them to the lines still without an ingredient. */
    fun withProposals(foods: List<NewFood>, proposals: Map<Long, IngredientProposal>): RecipeForm {
        val idsByName = foods.filter { it.id in proposals }.associate { it.name.normalizedName() to it.id }
        return copy(
            lines = lines.map { line ->
                if (line.ingredientId != null) line else line.copy(ingredientId = idsByName[line.ingredientName.normalizedName()])
            },
            newIngredients = newIngredients + proposals.mapValues { NewIngredient(it.value) },
        )
    }

    fun withNewIngredient(id: Long, proposal: IngredientProposal) =
        copy(newIngredients = newIngredients + (id to NewIngredient(proposal)))

    /** Confirms the proposed USDA entry; without one there is nothing to confirm. */
    fun confirmNewIngredient(id: Long) =
        updateNewIngredient(id) { if (it.proposal.food == null) it else it.copy(confirmed = true) }

    /** The user picked [food] instead of the proposed entry, which also confirms it. */
    fun withNewIngredientFood(id: Long, food: UsdaFood) =
        updateNewIngredient(id) { NewIngredient(it.proposal.copy(food = food), confirmed = true) }

    private fun updateNewIngredient(id: Long, transform: (NewIngredient) -> NewIngredient): RecipeForm {
        val existing = newIngredients[id] ?: return this
        return copy(newIngredients = newIngredients + (id to transform(existing)))
    }

    /** Whether [ingredientId] is a stored ingredient or a new one whose USDA entry is confirmed. */
    fun isReady(ingredientId: Long?): Boolean = when {
        ingredientId == null -> false
        ingredientId > 0 -> true
        else -> newIngredients[ingredientId]?.ready == true
    }

    /** New ingredients that have a USDA entry, with their temporary id, for previews and suggestions. */
    fun previewIngredients(): List<Ingredient> = newIngredients
        .filterValues { it.proposal.food != null }
        .map { (id, new) -> new.proposal.toIngredient(id) }

    /** The proposals to create on save: those a line uses. */
    fun newIngredientsToCreate(): Map<Long, IngredientProposal> {
        val used = lines.mapNotNull { it.ingredientId }.toSet()
        return newIngredients.filterKeys { it in used }.mapValues { it.value.proposal }
    }

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
        lines = lines.any { it.toDraft() == null || !isReady(it.ingredientId) },
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
    /** Some recipe field (all but the lines) is invalid. */
    val fields: Boolean get() = title || page || bookServings || ourServings || cookingTime

    val hasAny: Boolean get() = fields || lines
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
    /** English USDA search terms from the scan, while [ingredientId] is null. */
    val searchTerms: List<String> = emptyList(),
) {
    val complete: Boolean get() = ingredientId != null && grams > 0.0

    fun toDraft(): RecipeIngredientDraft? =
        if (complete) RecipeIngredientDraft(originalText, grams, ingredientId!!) else null

    companion object {
        fun from(line: RecipeIngredientDraft) = RecipeFormLine(line.originalText, line.grams, line.ingredientId)
    }
}

/** A new ingredient Claude proposed; its USDA entry must be confirmed before saving. */
data class NewIngredient(val proposal: IngredientProposal, val confirmed: Boolean = false) {
    val ready: Boolean get() = confirmed && proposal.food != null
}

/** Replaces the temporary ids of new ingredients with the ids they were created with. */
fun RecipeDraft.withIngredientIds(ids: Map<Long, Long>) =
    copy(lines = lines.map { line -> ids[line.ingredientId]?.let { line.copy(ingredientId = it) } ?: line })

private fun String.normalizedName() = trim().lowercase()

private fun String.toPositiveIntOrNull(): Int? = trim().toIntOrNull()?.takeIf { it > 0 }
