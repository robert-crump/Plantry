package com.example.plantry.data

/** The ingredient list's Prüfstatus filter. */
enum class ReviewFilter { ALL, REVIEWED, UNREVIEWED }

/** The ingredient list's Verwendung filter; "used" means at least one recipe (buy-as links followed). */
enum class UsageFilter { ALL, USED, UNUSED }

/** The ingredient list's Herkunft filter: the seed import, or everything added since (Claude, barcode, by hand). */
enum class OriginFilter { ALL, SEED, ADDED }

/** The ingredient list's filter chips; the two of each filter exclude each other, none on means Alle. */
enum class IngredientChip(override val group: Any) : FilterChoice {
    REVIEWED(ReviewFilter::class),
    UNREVIEWED(ReviewFilter::class),
    USED(UsageFilter::class),
    UNUSED(UsageFilter::class),
    SEED(OriginFilter::class),
    ADDED(OriginFilter::class),
}

object IngredientFilters {

    /** The [ingredients] matching all of [chips], in their order. */
    fun apply(ingredients: List<Ingredient>, recipeCounts: Map<Long, Int>, chips: Collection<IngredientChip>): List<Ingredient> =
        apply(
            ingredients,
            recipeCounts,
            review = when {
                IngredientChip.REVIEWED in chips -> ReviewFilter.REVIEWED
                IngredientChip.UNREVIEWED in chips -> ReviewFilter.UNREVIEWED
                else -> ReviewFilter.ALL
            },
            usage = when {
                IngredientChip.USED in chips -> UsageFilter.USED
                IngredientChip.UNUSED in chips -> UsageFilter.UNUSED
                else -> UsageFilter.ALL
            },
            origin = when {
                IngredientChip.SEED in chips -> OriginFilter.SEED
                IngredientChip.ADDED in chips -> OriginFilter.ADDED
                else -> OriginFilter.ALL
            },
        )

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
