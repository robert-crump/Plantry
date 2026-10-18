package com.example.plantry.data

import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** The numbers a recipe is judged by at a glance. */
data class RecipeStats(
    /** Sum of [PlantPoints] over the recipe's distinct plants, mixes resolved to their components. */
    val plantPoints: Double,
    val proteinPerPortion: Double,
    val carbsPerPortion: Double,
    /** Null in cooking log entries logged before kcal were snapshotted. */
    val kcalPerPortion: Double? = null,
    /** Null in cooking log entries logged before fibre was snapshotted. */
    val fibrePerPortion: Double? = null,
) {
    /** Rounded to whole numbers for compact display, e.g. on Speiseplan. */
    val roundedPlantPoints: Int get() = plantPoints.roundToInt()
    val roundedProtein: Int get() = proteinPerPortion.roundToInt()
    val roundedCarbs: Int get() = carbsPerPortion.roundToInt()
    val roundedKcal: Int? get() = kcalPerPortion?.roundToInt()
    val roundedFibre: Int? get() = fibrePerPortion?.roundToInt()

    companion object {
        /** Lines whose ingredient is missing from [ingredients] are skipped. */
        fun of(recipe: Recipe, lines: List<RecipeLine>, ingredients: Map<Long, Ingredient>, contents: MixContents = emptyMap()): RecipeStats {
            val nutrition = RecipeNutrition.calculate(
                nutritionLines(lines.sortedBy { it.position }, ingredients),
                recipe.servings,
            ).perPortion
            return RecipeStats(
                plantPoints = Mixes.plantPoints(lines.map { it.ingredientId }.filter { it in ingredients }, ingredients, contents),
                proteinPerPortion = nutrition.protein,
                carbsPerPortion = nutrition.carbs,
                kcalPerPortion = nutrition.kcal,
                fibrePerPortion = nutrition.fibre,
            )
        }
    }
}

/** What a cooking log entry keeps of its recipe, so later edits or deletion don't change it. */
data class RecipeSnapshot(
    val title: String,
    val stats: RecipeStats,
    /** The recipe's plants, see [CookLogPlant.of]. */
    val plants: List<CookLogPlant> = emptyList(),
) {
    companion object {
        /** Lines are matched to [recipe] by id. */
        fun of(recipe: Recipe, lines: List<RecipeLine>, ingredients: Map<Long, Ingredient>, contents: MixContents = emptyMap()): RecipeSnapshot {
            val recipeLines = lines.filter { it.recipeId == recipe.id }
            return RecipeSnapshot(
                recipe.title,
                RecipeStats.of(recipe, recipeLines, ingredients, contents),
                CookLogPlant.of(recipeLines.map { it.ingredientId }, ingredients, contents),
            )
        }

        /** The current snapshot of a stored recipe; null if it doesn't exist. */
        suspend fun load(recipeId: Long, recipes: RecipeDao, ingredients: IngredientDao): RecipeSnapshot? {
            val recipe = recipes.getById(recipeId) ?: return null
            return of(
                recipe,
                recipes.getLines(recipeId),
                ingredients.observeAll().first().associateBy { it.id },
                Mixes.contents(ingredients.getMixComponents()),
            )
        }
    }
}
