package com.example.plantry.data

import java.text.Collator
import java.time.LocalDate
import java.util.Locale

enum class RecipeSort {
    /** A–Z, the default. */
    TITLE,

    /** Most protein per portion first. */
    PROTEIN,

    /** Quickest first. */
    COOKING_TIME,

    /** Most plant points first. */
    PLANT_POINTS,

    /** Never cooked first, then the one cooked longest ago. */
    LAST_COOKED,
}

/** What the recipe list shows; the empty filter shows every recipe. */
data class RecipeFilter(
    /** Recipes using at least one of these are shown, ranked by how many of them they use. */
    val ingredientIds: Set<Long> = emptySet(),
    val maxCookingMinutes: Int? = null,
    /** A recipe's [Recipe.source], compared ignoring case and surrounding blanks. */
    val source: String? = null,
) {
    val isEmpty: Boolean get() = ingredientIds.isEmpty() && maxCookingMinutes == null && source == null
}

/** One row of the recipe list with the values it can be sorted by. */
data class RecipeListItem(
    val recipe: Recipe,
    val proteinPerPortion: Double,
    /** Sum of [PlantPoints] over the recipe's distinct plants, buy-as links followed. */
    val plantPoints: Double,
    val lastCookedOn: LocalDate?,
    /** How many of the filter's ingredients the recipe uses; 0 without an ingredient filter. */
    val matchedIngredients: Int,
)

object RecipeQuery {

    private val titleCollator: Collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.SECONDARY }

    /**
     * The recipes passing [filter], sorted by [sort]. With an ingredient filter, recipes using more
     * of the chosen ingredients come first and [sort] only breaks ties; the title breaks the rest.
     *
     * An ingredient counts as used when a line's ingredient and the chosen one are bought as the
     * same thing (same end of their buy-as chains), so "Reis" also finds "Reis, gekocht". Lines
     * with a staple are never a match. Lines whose ingredient is missing are skipped.
     */
    fun run(
        recipes: List<Recipe>,
        lines: List<RecipeIngredient>,
        ingredients: Map<Long, Ingredient>,
        lastCooked: Map<Long, LocalDate>,
        filter: RecipeFilter,
        sort: RecipeSort,
    ): List<RecipeListItem> {
        val linesByRecipe = lines.groupBy { it.recipeId }
        val wantedRoots = filter.ingredientIds.mapNotNull { id ->
            ingredients[id]?.let { WeekSummary.buyAsRoot(it, ingredients).id }
        }
        val source = filter.source?.trim()
        return recipes
            .filter { recipe -> filter.maxCookingMinutes?.let { recipe.cookingTimeMinutes <= it } ?: true }
            .filter { recipe -> source == null || recipe.source.trim().equals(source, ignoreCase = true) }
            .map { recipe -> item(recipe, linesByRecipe[recipe.id].orEmpty(), ingredients, lastCooked, wantedRoots) }
            .filter { wantedRoots.isEmpty() || it.matchedIngredients > 0 }
            .sortedWith(
                compareByDescending<RecipeListItem> { it.matchedIngredients }
                    .then(comparator(sort))
                    .thenBy(titleCollator) { it.recipe.title },
            )
    }

    /** The distinct non-blank sources of [recipes], A–Z; for the book filter. */
    fun sources(recipes: List<Recipe>): List<String> =
        recipes.map { it.source.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedWith(titleCollator)

    private fun item(
        recipe: Recipe,
        recipeLines: List<RecipeIngredient>,
        ingredients: Map<Long, Ingredient>,
        lastCooked: Map<Long, LocalDate>,
        wantedRoots: List<Long>,
    ): RecipeListItem {
        val used = recipeLines.mapNotNull { ingredients[it.ingredientId] }
        val roots = used.mapTo(mutableSetOf()) { WeekSummary.buyAsRoot(it, ingredients) }
        val matchableRoots = used.filterNot { it.staple }.mapTo(mutableSetOf()) { WeekSummary.buyAsRoot(it, ingredients).id }
        val nutrition = RecipeNutrition.calculate(
            nutritionLines(recipeLines.sortedBy { it.position }.map { it.toDraft() }, ingredients),
            recipe.ourServings,
        )
        return RecipeListItem(
            recipe = recipe,
            proteinPerPortion = nutrition.perPortion.protein,
            plantPoints = roots.sumOf { it.plantPoints.value },
            lastCookedOn = lastCooked[recipe.id],
            matchedIngredients = wantedRoots.count { it in matchableRoots },
        )
    }

    private fun comparator(sort: RecipeSort): Comparator<RecipeListItem> = when (sort) {
        RecipeSort.TITLE -> compareBy(titleCollator) { it.recipe.title }
        RecipeSort.PROTEIN -> compareByDescending { it.proteinPerPortion }
        RecipeSort.COOKING_TIME -> compareBy { it.recipe.cookingTimeMinutes }
        RecipeSort.PLANT_POINTS -> compareByDescending { it.plantPoints }
        // nullsFirst: never cooked comes before any date.
        RecipeSort.LAST_COOKED -> compareBy(nullsFirst()) { it.lastCookedOn }
    }
}
