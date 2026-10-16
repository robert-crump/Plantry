package com.example.plantry.data

/** The ingredient list's filter chips; they combine freely, none on shows every ingredient. */
enum class IngredientChip : FilterChoice {
    /** At least one recipe uses it (buy-as links followed). */
    USED;

    override val group: Any? get() = null
}

object IngredientFilters {

    /**
     * The [ingredients] matching all of [chips], in their order; [recipeCounts] as from
     * [RecipeQuery.recipeCounts] (missing ids mean 0).
     */
    fun apply(ingredients: List<Ingredient>, recipeCounts: Map<Long, Int>, chips: Collection<IngredientChip>): List<Ingredient> =
        ingredients.filter { ingredient ->
            chips.all { chip ->
                when (chip) {
                    IngredientChip.USED -> (recipeCounts[ingredient.id] ?: 0) > 0
                }
            }
        }
}
