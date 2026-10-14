package com.example.plantry.data

import java.text.Collator
import java.time.LocalDate
import java.util.Locale

enum class RecipeSort {
    /** A–Z, the default. */
    TITLE,

    /** Most protein per portion first. */
    PROTEIN,

    /** Shortest Gesamtzeit first. */
    TOTAL_TIME,

    /** Shortest Arbeitszeit first. */
    ACTIVE_TIME,

    /** Most plant points first. */
    PLANT_POINTS,

    /** Never cooked first, then the one cooked longest ago. */
    LAST_COOKED,
}

/** The recipe list's filter chips: any number of ingredients, the two times and at most one dish. */
sealed interface RecipeChip : FilterChoice {

    data class WithIngredient(val ingredientId: Long) : RecipeChip {
        override val group: Any? get() = null
    }

    /** At most this Gesamtzeit: ready soon. */
    data class MaxTotalTime(val minutes: Int) : RecipeChip {
        override val group: Any get() = MaxTotalTime::class
    }

    /** At most this Arbeitszeit: little hands-on work, however long it then cooks. */
    data class MaxActiveTime(val minutes: Int) : RecipeChip {
        override val group: Any get() = MaxActiveTime::class
    }

    data class OfDish(val dish: Dish) : RecipeChip {
        override val group: Any get() = OfDish::class
    }

    companion object {
        /** "Schnell fertig". */
        val QUICK = MaxTotalTime(30)

        /** "Wenig Arbeit". */
        val EASY = MaxActiveTime(20)

        /** "Hauptgericht" and "Snack", only when [recipes] hold both; else either chip would match them all. */
        fun dishes(recipes: List<Recipe>): List<OfDish> {
            val present = recipes.mapTo(mutableSetOf()) { it.dish }
            return if (present.size == Dish.entries.size) Dish.entries.map(::OfDish) else emptyList()
        }
    }
}

/** What the recipe list shows; the empty filter shows every recipe. */
data class RecipeFilter(
    /** Recipes using at least one of these are shown, ranked by how many of them they use. */
    val ingredientIds: Set<Long> = emptySet(),
    val maxTotalMinutes: Int? = null,
    val maxActiveMinutes: Int? = null,
    val dish: Dish? = null,
) {
    companion object {
        /** The filter the [chips] stand for; the ingredients keep the chips' order. */
        fun of(chips: List<RecipeChip>) = RecipeFilter(
            ingredientIds = chips.filterIsInstance<RecipeChip.WithIngredient>().mapTo(LinkedHashSet()) { it.ingredientId },
            maxTotalMinutes = chips.filterIsInstance<RecipeChip.MaxTotalTime>().firstOrNull()?.minutes,
            maxActiveMinutes = chips.filterIsInstance<RecipeChip.MaxActiveTime>().firstOrNull()?.minutes,
            dish = chips.filterIsInstance<RecipeChip.OfDish>().firstOrNull()?.dish,
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
            .filter { recipe -> atMost(recipe.totalTimeMinutes, filter.maxTotalMinutes) }
            .filter { recipe -> atMost(recipe.activeTimeMinutes, filter.maxActiveMinutes) }
            .filter { recipe -> filter.dish == null || recipe.dish == filter.dish }
            .map { recipe -> item(recipe, linesByRecipe[recipe.id].orEmpty(), ingredients, lastCooked, wanted) }
            .filter { wanted.isEmpty() || it.matchedIngredients > 0 }
            .sortedWith(
                compareByDescending<RecipeListItem> { it.matchedIngredients }
                    .then(comparator(sort))
                    .thenBy(titleCollator) { it.recipe.title },
            )
    }

    private fun atMost(minutes: Int?, max: Int?) = max == null || minutes == null || minutes <= max

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
        RecipeSort.TOTAL_TIME -> compareBy(nullsLast()) { it.recipe.totalTimeMinutes }
        RecipeSort.ACTIVE_TIME -> compareBy(nullsLast()) { it.recipe.activeTimeMinutes }
        RecipeSort.PLANT_POINTS -> compareByDescending { it.plantPoints }
        // nullsFirst: never cooked comes before any date.
        RecipeSort.LAST_COOKED -> compareBy(nullsFirst()) { it.lastCookedOn }
    }
}
