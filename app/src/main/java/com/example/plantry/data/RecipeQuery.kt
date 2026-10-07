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

/** The recipe list's filter chips: any number of ingredients, at most one cooking time. */
sealed interface RecipeChip : FilterChoice {

    data class WithIngredient(val ingredientId: Long) : RecipeChip {
        override val group: Any? get() = null
    }

    data class MaxCookingTime(val minutes: Int) : RecipeChip {
        override val group: Any get() = MaxCookingTime::class
    }

    companion object {
        val COOKING_TIMES = listOf(MaxCookingTime(20), MaxCookingTime(45))
    }
}

/** What the recipe list shows; the empty filter shows every recipe. */
data class RecipeFilter(
    /** Recipes using at least one of these are shown, ranked by how many of them they use. */
    val ingredientIds: Set<Long> = emptySet(),
    val maxCookingMinutes: Int? = null,
) {
    companion object {
        /** The filter the [chips] stand for; the ingredients keep the chips' order. */
        fun of(chips: List<RecipeChip>) = RecipeFilter(
            ingredientIds = chips.filterIsInstance<RecipeChip.WithIngredient>().mapTo(LinkedHashSet()) { it.ingredientId },
            maxCookingMinutes = chips.filterIsInstance<RecipeChip.MaxCookingTime>().firstOrNull()?.minutes,
        )
    }
}

/** One row of the recipe list with the values it can be sorted by. */
data class RecipeListItem(
    val recipe: Recipe,
    val stats: RecipeStats,
    val lastCookedOn: LocalDate?,
    /** How many of the filter's ingredients the recipe uses; 0 without an ingredient filter. */
    val matchedIngredients: Int,
    val highlights: Set<RecipeHighlight> = emptySet(),
) {
    val proteinPerPortion: Double get() = stats.proteinPerPortion

    /** Sum of [PlantPoints] over the recipe's distinct ingredients. */
    val plantPoints: Double get() = stats.plantPoints
}

object RecipeQuery {

    private val titleCollator: Collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.SECONDARY }

    /**
     * The recipes passing [filter], sorted by [sort]. With an ingredient filter, recipes using more
     * of the chosen ingredients come first and [sort] only breaks ties; the title breaks the rest.
     * Lines whose ingredient is missing are skipped.
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
        val wanted = filter.ingredientIds.filter { it in ingredients }
        return recipes
            // A recipe without a time is never filtered out: it may well be quick.
            .filter { recipe -> filter.maxCookingMinutes?.let { max -> recipe.cookingTimeMinutes?.let { it <= max } } ?: true }
            .map { recipe -> item(recipe, linesByRecipe[recipe.id].orEmpty(), ingredients, lastCooked, wanted) }
            .filter { wanted.isEmpty() || it.matchedIngredients > 0 }
            .sortedWith(
                compareByDescending<RecipeListItem> { it.matchedIngredients }
                    .then(comparator(sort))
                    .thenBy(titleCollator) { it.recipe.title },
            )
    }

    /** The recipes whose title contains [query] (ignoring case and surrounding blanks), A–Z; for "Kocheintrag". */
    fun byTitle(recipes: List<Recipe>, query: String): List<Recipe> {
        val wanted = query.trim()
        return recipes.filter { it.title.contains(wanted, ignoreCase = true) }.sortedWith(compareBy(titleCollator) { it.title })
    }

    /** How many recipes use each ingredient, keyed by id; ingredients no recipe uses are left out. */
    fun recipeCounts(lines: List<RecipeIngredient>, ingredients: Map<Long, Ingredient>): Map<Long, Int> =
        lines.filter { it.ingredientId in ingredients }
            .groupBy { it.ingredientId }
            .mapValues { (_, used) -> used.distinctBy { it.recipeId }.size }

    private fun item(
        recipe: Recipe,
        recipeLines: List<RecipeIngredient>,
        ingredients: Map<Long, Ingredient>,
        lastCooked: Map<Long, LocalDate>,
        wanted: List<Long>,
    ): RecipeListItem {
        val used = recipeLines.mapTo(mutableSetOf()) { it.ingredientId }
        val stats = RecipeStats.of(recipe, recipeLines, ingredients)
        return RecipeListItem(
            recipe = recipe,
            stats = stats,
            lastCookedOn = lastCooked[recipe.id],
            matchedIngredients = wanted.count { it in used },
            highlights = RecipeHighlight.of(stats, hasLines = recipeLines.isNotEmpty()),
        )
    }

    private fun comparator(sort: RecipeSort): Comparator<RecipeListItem> = when (sort) {
        RecipeSort.TITLE -> compareBy(titleCollator) { it.recipe.title }
        RecipeSort.PROTEIN -> compareByDescending { it.proteinPerPortion }
        // nullsLast: recipes without a time can't claim to be quick.
        RecipeSort.COOKING_TIME -> compareBy(nullsLast()) { it.recipe.cookingTimeMinutes }
        RecipeSort.PLANT_POINTS -> compareByDescending { it.plantPoints }
        // nullsFirst: never cooked comes before any date.
        RecipeSort.LAST_COOKED -> compareBy(nullsFirst()) { it.lastCookedOn }
    }
}
