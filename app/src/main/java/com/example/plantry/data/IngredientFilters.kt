package com.example.plantry.data

/** The ingredient list's Prüfstatus filter. */
enum class ReviewFilter { ALL, REVIEWED, UNREVIEWED }

/** The ingredient list's Verwendung filter; "used" means at least one recipe (buy-as links followed). */
enum class UsageFilter { ALL, USED, UNUSED }

/** The ingredient list's Herkunft filter: the seed import, or everything added since (Claude, barcode, by hand). */
enum class OriginFilter { ALL, SEED, ADDED }

object IngredientFilters {

    /** The list opens with these each time the Zutaten tab is opened. */
    val DEFAULT_REVIEW = ReviewFilter.ALL
    val DEFAULT_USAGE = UsageFilter.ALL
    val DEFAULT_ORIGIN = OriginFilter.ALL

    /**
     * The [ingredients] matching all filters, in their order; [recipeCounts] as from
     * [RecipeQuery.recipeCounts] (missing ids mean 0).
     */
    fun apply(
        ingredients: List<Ingredient>,
        recipeCounts: Map<Long, Int>,
        review: ReviewFilter,
        usage: UsageFilter,
        origin: OriginFilter = OriginFilter.ALL,
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
        val originMatches = when (origin) {
            OriginFilter.ALL -> true
            OriginFilter.SEED -> ingredient.origin == IngredientOrigin.SEED
            OriginFilter.ADDED -> ingredient.origin != IngredientOrigin.SEED
        }
        reviewMatches && usageMatches && originMatches
    }
}
