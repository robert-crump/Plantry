package com.example.plantry.data

import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** The numbers a recipe is judged by at a glance. */
data class RecipeStats(
    /** Sum of [PlantPoints] over the recipe's distinct plants, buy-as links followed. */
    val plantPoints: Double,
    val proteinPerPortion: Double,
    val carbsPerPortion: Double,
) {
    /** Rounded to whole numbers for compact display, e.g. on Kochen. */
    val roundedPlantPoints: Int get() = plantPoints.roundToInt()
    val roundedProtein: Int get() = proteinPerPortion.roundToInt()
    val roundedCarbs: Int get() = carbsPerPortion.roundToInt()

    companion object {
        /** Lines whose ingredient is missing from [ingredients] are skipped. */
        fun of(recipe: Recipe, lines: List<RecipeIngredient>, ingredients: Map<Long, Ingredient>): RecipeStats {
            val roots = lines.mapNotNullTo(mutableSetOf()) { line ->
                ingredients[line.ingredientId]?.let { buyAsRoot(it, ingredients) }
            }
            val nutrition = RecipeNutrition.calculate(
                nutritionLines(lines.sortedBy { it.position }.map { it.toDraft() }, ingredients),
                recipe.ourServings,
            ).perPortion
            return RecipeStats(
                plantPoints = roots.sumOf { it.plantPoints.value },
                proteinPerPortion = nutrition.protein,
                carbsPerPortion = nutrition.carbs,
            )
        }
    }
}

/** What a cooking log entry keeps of its recipe, so later edits or deletion don't change it. */
data class RecipeSnapshot(val title: String, val stats: RecipeStats) {
    companion object {
        /** Lines are matched to [recipe] by id. */
        fun of(recipe: Recipe, lines: List<RecipeIngredient>, ingredients: Map<Long, Ingredient>) =
            RecipeSnapshot(recipe.title, RecipeStats.of(recipe, lines.filter { it.recipeId == recipe.id }, ingredients))

        /** The current snapshot of a stored recipe; null if it doesn't exist. */
        suspend fun load(recipeId: Long, recipes: RecipeDao, ingredients: IngredientDao): RecipeSnapshot? {
            val recipe = recipes.getById(recipeId) ?: return null
            return of(recipe, recipes.getLines(recipeId), ingredients.observeAll().first().associateBy { it.id })
        }
    }
}
