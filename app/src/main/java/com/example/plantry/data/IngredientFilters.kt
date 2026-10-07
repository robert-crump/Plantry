package com.example.plantry.data

/** The ingredient list's Prüfstatus filter. */
enum class ReviewFilter { ALL, REVIEWED, UNREVIEWED }

/** The ingredient list's Verwendung filter; "used" means at least one recipe (buy-as links followed). */
enum class UsageFilter { ALL, USED, UNUSED }

object IngredientFilters {

    /** The list opens with these each time the Zutaten tab is opened. */
    val DEFAULT_REVIEW = ReviewFilter.ALL
    val DEFAULT_USAGE = UsageFilter.ALL

    /**
     * The [ingredients] matching both filters, in their order; [recipeCounts] as from
     * [RecipeQuery.recipeCounts] (missing ids mean 0).
     */
    fun apply(
        ingredients: List<Ingredient>,
        recipeCounts: Map<Long, Int>,
        review: ReviewFilter,
        usage: UsageFilter,
    ): List<Ingredient> = ingredients.filter { ingredient ->
        val reviewMatches = when (review) {
            ReviewFilter.ALL -> true
            ReviewFilter.REVIEWED -> ingredient.reviewed
            ReviewFilter.UNREVIEWED -> !ingredient.reviewed
        }
        val used = (recipeCounts[ingredient.id] ?: 0) > 0
        val usageMatches = when (usage) {
            UsageFilter.ALL -> true
            UsageFilter.USED -> used
            UsageFilter.UNUSED -> !used
        }
        reviewMatches && usageMatches
    }
}
