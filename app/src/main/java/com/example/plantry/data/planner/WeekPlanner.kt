package com.example.plantry.data.planner

import com.example.plantry.data.CookLogDao
import com.example.plantry.data.CookingStats
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientDao
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeDao
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.RecipeNutrition
import com.example.plantry.data.SlotRef
import com.example.plantry.data.WeekPlanRepository
import com.example.plantry.data.nutritionLines
import com.example.plantry.data.toDraft
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.random.Random

/** Fills the week's menu with suggestions from [MealPlanner]. */
class WeekPlanner(
    private val recipeDao: RecipeDao,
    private val ingredientDao: IngredientDao,
    private val cookLogDao: CookLogDao,
    private val weekPlan: WeekPlanRepository,
    /** Read on every suggestion, so a changed setting applies right away. */
    private val cooldownDays: () -> Int,
    private val random: Random = Random.Default,
) {
    /** Fills the empty slots of [weekStart]; manual picks stay. Returns how many slots were filled. */
    suspend fun suggestWeek(weekStart: LocalDate, today: LocalDate): Int {
        val candidates = candidates(today)
        val planner = MealPlanner.default(cooldownDays())
        return weekPlan.fillEmpty(weekStart) { onMenu, count -> planner.suggest(candidates, onMenu, count, random) }
    }

    /** Replaces the recipe in [slot] with another suggestion. Returns false if there was none. */
    suspend fun swap(slot: SlotRef, today: LocalDate): Boolean {
        val candidates = candidates(today)
        val planner = MealPlanner.default(cooldownDays())
        return weekPlan.swap(slot) { onMenu -> planner.suggest(candidates, onMenu, 1, random).firstOrNull() }
    }

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
            return recipes.map { recipe ->
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
