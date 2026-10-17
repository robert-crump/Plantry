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

    /** The [ingredients] matching all of [chips], in their order. */
    fun apply(ingredients: List<Ingredient>, chips: Collection<IngredientChip>): List<Ingredient> =
        ingredients.filter { ingredient ->
            val nutrition = ingredient.nutrition
            chips.all { chip ->
                when (chip) {
                    IngredientChip.HIGH_PROTEIN -> nutrition.protein >= PROTEIN_MIN
                    IngredientChip.HIGH_CARBS -> nutrition.carbs >= CARBS_MIN
                    IngredientChip.HIGH_FIBRE -> nutrition.fibre >= FIBRE_MIN
                }
            }
        }
}
