package com.example.plantry.data.planner

import com.example.plantry.data.CookLogDao
import com.example.plantry.data.CookingStats
import com.example.plantry.data.Dish
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientDao
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeDao
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.RecipeNutrition
import com.example.plantry.data.nutritionLines
import com.example.plantry.data.toDraft
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.random.Random

/** Suggests main dishes from the collection with [MealPlanner], scored by cooldown and protein; never snacks. */
class RecipeSuggester(
    private val recipeDao: RecipeDao,
    private val ingredientDao: IngredientDao,
    private val cookLogDao: CookLogDao,
    /** Read on every suggestion, so a changed setting applies right away. */
    private val cooldownDays: () -> Int,
    private val random: Random = Random.Default,
) {
    /** Up to [count] distinct recipe ids, none of them in [exclude]. */
    suspend fun suggest(today: LocalDate, count: Int, exclude: Set<Long> = emptySet()): List<Long> =
        MealPlanner.default(cooldownDays()).suggest(candidates(today), exclude, count, random)

    private suspend fun candidates(today: LocalDate): List<Candidate> = candidates(
        recipes = recipeDao.observeAll().first(),
        lines = recipeDao.getAllLines(),
        ingredients = ingredientDao.observeAll().first(),
        lastCooked = cookLogDao.getLastCooked().associate { it.recipeId to it.lastCookedOn },
        today = today,
    )

    companion object {
        fun candidates(
            recipes: List<Recipe>,
            lines: List<RecipeIngredient>,
            ingredients: List<Ingredient>,
            lastCooked: Map<Long, LocalDate>,
            today: LocalDate,
        ): List<Candidate> {
            val ingredientsById = ingredients.associateBy { it.id }
            val linesByRecipe = lines.groupBy { it.recipeId }
            return recipes.filter { it.dish == Dish.MAIN }.map { recipe ->
                val recipeLines = linesByRecipe[recipe.id].orEmpty().sortedBy { it.position }.map { it.toDraft() }
                val nutrition = RecipeNutrition.calculate(nutritionLines(recipeLines, ingredientsById), recipe.ourServings)
                Candidate(
                    recipeId = recipe.id,
                    daysSinceLastCooked = CookingStats.daysSinceLastCooked(listOfNotNull(lastCooked[recipe.id]), today),
                    proteinPerPortion = nutrition.perPortion.protein,
                )
            }
        }
    }
}
