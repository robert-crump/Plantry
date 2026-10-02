package com.example.plantry.data

/**
 * Totals of a week's menu, counted whether its recipes are cooked yet or not.
 * Ingredients with a buy-as link count as their buy-as target, so "rice, cooked" and
 * "rice, dry" are one ingredient and one plant.
 */
data class WeekSummary(
    val distinctIngredients: Int,
    /** Sum of [PlantPoints] per distinct plant: 1 per plant, ¼ per herb or spice. */
    val plantPoints: Double,
    /** Mean protein per portion over the menu's recipes; null for an empty menu. */
    val averageProteinPerPortion: Double?,
) {
    companion object {
        /**
         * The summary of the [recipes] on the menu. [lines] may contain lines of other recipes;
         * lines whose ingredient is missing from [ingredients] are skipped.
         */
        fun of(recipes: List<Recipe>, lines: List<RecipeIngredient>, ingredients: Map<Long, Ingredient>): WeekSummary {
            val linesByRecipe = lines.groupBy { it.recipeId }
            val menuLines = recipes.flatMap { linesByRecipe[it.id].orEmpty() }
            val distinct = menuLines
                .mapNotNull { ingredients[it.ingredientId] }
                .mapTo(mutableSetOf()) { buyAsRoot(it, ingredients) }
            val proteins = recipes.map { recipe ->
                val recipeLines = linesByRecipe[recipe.id].orEmpty().map { it.toDraft() }
                RecipeNutrition.calculate(nutritionLines(recipeLines, ingredients), recipe.ourServings).perPortion.protein
            }
            return WeekSummary(
                distinctIngredients = distinct.size,
                plantPoints = distinct.sumOf { it.plantPoints.value },
                averageProteinPerPortion = proteins.takeIf { it.isNotEmpty() }?.average(),
            )
        }

        /** The end of [ingredient]'s buy-as chain; the ingredient itself if it has no link. */
        fun buyAsRoot(ingredient: Ingredient, ingredients: Map<Long, Ingredient>): Ingredient =
            resolveBuyAs(ingredient, ingredients).ingredient
    }
}
