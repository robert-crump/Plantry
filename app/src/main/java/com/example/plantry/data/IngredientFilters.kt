package com.example.plantry.data

/** The ingredient list's nutrient chips, per 100 g; they combine, none on shows every ingredient. */
enum class IngredientChip : FilterChoice {
    /** At least [IngredientFilters.PROTEIN_MIN] g protein. */
    HIGH_PROTEIN,

    /** At least [IngredientFilters.CARBS_MIN] g carbs. */
    HIGH_CARBS,

    /** At least [IngredientFilters.FIBRE_MIN] g fibre, the EU's "ballaststoffreich". */
    HIGH_FIBRE;

    override val group: Any? get() = null
}

object IngredientFilters {
    const val PROTEIN_MIN = 12.0
    const val CARBS_MIN = 50.0
    const val FIBRE_MIN = 6.0

    /**
     * The [ingredients] matching all of [chips] and whose name contains every word of [search] (see
     * [RecipeQuery.titleMatches]), in their order.
     */
    fun apply(ingredients: List<Ingredient>, chips: Collection<IngredientChip>, search: String = ""): List<Ingredient> =
        ingredients.filter { ingredient ->
            chips.all { amount(it, ingredient.nutrition) >= min(it) } && RecipeQuery.titleMatches(ingredient.name, search.trim())
        }

    /** The grams per 100 g from which an ingredient counts for [chip]. */
    fun min(chip: IngredientChip): Double = when (chip) {
        IngredientChip.HIGH_PROTEIN -> PROTEIN_MIN
        IngredientChip.HIGH_CARBS -> CARBS_MIN
        IngredientChip.HIGH_FIBRE -> FIBRE_MIN
    }

    /** The grams per 100 g of the nutrient [chip] is about. */
    fun amount(chip: IngredientChip, nutrition: Nutrition): Double = when (chip) {
        IngredientChip.HIGH_PROTEIN -> nutrition.protein
        IngredientChip.HIGH_CARBS -> nutrition.carbs
        IngredientChip.HIGH_FIBRE -> nutrition.fibre
    }
}
