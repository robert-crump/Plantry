package com.example.plantry.data.planner

import com.example.plantry.data.CookLogDao
import com.example.plantry.data.CookingStats
import com.example.plantry.data.Dish
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientDao
import com.example.plantry.data.Mixes
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeDao
import com.example.plantry.data.RecipeLine
import com.example.plantry.data.RecipeNutrition
import com.example.plantry.data.nutritionLines
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/**
 * Suggests recipes from the collection with [MealPlanner]: main dishes scored by cooldown, protein
 * and the plant points they add to this week, and the weekend's plant-points boost from breakfasts
 * and snacks.
 */
class RecipeSuggester(
    private val recipeDao: RecipeDao,
    private val ingredientDao: IngredientDao,
    private val cookLogDao: CookLogDao,
    /** Read on every suggestion, so a changed setting applies right away. */
    private val cooldownDays: () -> Int,
    private val random: Random = Random.Default,
) {
    /** Up to [count] distinct main-dish ids, none of them in [exclude]. */
    suspend fun suggest(today: LocalDate, count: Int, exclude: Set<Long> = emptySet()): List<Long> {
        val state = load(today)
        val candidates = state.candidates(MAIN_DISHES)
        return MealPlanner.mainDishes(cooldownDays(), candidates, state.week.goalReached)
            .suggest(candidates, exclude, count, random)
    }

    /**
     * Up to [count] distinct breakfast or snack ids, none of them in [exclude], that add plant points
     * to this week; none unless [boostWanted].
     */
    suspend fun boost(today: LocalDate, count: Int, exclude: Set<Long> = emptySet()): List<Long> {
        val state = load(today)
        if (!boostWanted(today, state.week)) return emptyList()
        val candidates = boostCandidates(state.candidates(BOOST_DISHES))
        return MealPlanner.boost(cooldownDays(), candidates).suggest(candidates, exclude, count, random)
    }

    /** This week's plants so far, as the suggestions weigh them. */
    suspend fun week(today: LocalDate): WeekPlants = load(today).week

    private class State(
        val recipes: List<Recipe>,
        val lines: List<RecipeLine>,
        val ingredients: List<Ingredient>,
        val lastCooked: Map<Long, LocalDate>,
        val today: LocalDate,
        val week: WeekPlants,
    ) {
        fun candidates(dishes: Set<Dish>) = candidates(recipes, lines, ingredients, lastCooked, today, week, dishes)
    }

    private suspend fun load(today: LocalDate): State {
        val lines = recipeDao.getAllLines()
        val ingredients = ingredientDao.observeAll().first()
        val week = WeekPlants.of(
            today = today,
            entries = cookLogDao.observeHistory().first(),
            lines = lines,
            ingredients = ingredients.associateBy { it.id },
            contents = Mixes.contents(ingredientDao.getMixComponents()),
        )
        return State(
            recipes = recipeDao.observeAll().first(),
            lines = lines,
            ingredients = ingredients,
            lastCooked = cookLogDao.getLastCooked().associate { it.recipeId to it.lastCookedOn },
            today = today,
            week = week,
        )
    }

    companion object {
        val MAIN_DISHES = setOf(Dish.MAIN)
        val BOOST_DISHES = setOf(Dish.BREAKFAST, Dish.SNACK)

        /** The boost card shows on Friday, Saturday and Sunday while the week is short of the goal. */
        fun boostWanted(today: LocalDate, week: WeekPlants): Boolean =
            today.dayOfWeek >= DayOfWeek.FRIDAY && !week.goalReached

        /** Only recipes that add plant points boost them; none at all means no card. */
        fun boostCandidates(candidates: List<Candidate>): List<Candidate> = candidates.filter { it.newPlantPoints > 0 }

        fun candidates(
            recipes: List<Recipe>,
            lines: List<RecipeLine>,
            ingredients: List<Ingredient>,
            lastCooked: Map<Long, LocalDate>,
            today: LocalDate,
            week: WeekPlants = WeekPlants(emptySet(), ingredients.associateBy { it.id }, emptyMap()),
            dishes: Set<Dish> = MAIN_DISHES,
        ): List<Candidate> {
            val ingredientsById = ingredients.associateBy { it.id }
            val linesByRecipe = lines.groupBy { it.recipeId }
            return recipes.filter { it.dish in dishes }.map { recipe ->
                val recipeLines = linesByRecipe[recipe.id].orEmpty().sortedBy { it.position }
                val nutrition = RecipeNutrition.calculate(nutritionLines(recipeLines, ingredientsById), recipe.servings)
                Candidate(
                    recipeId = recipe.id,
                    daysSinceLastCooked = CookingStats.daysSinceLastCooked(listOfNotNull(lastCooked[recipe.id]), today),
                    proteinPerPortion = nutrition.perPortion.protein,
                    newPlantPoints = week.newPoints(recipeLines.map { it.ingredientId }),
                )
            }
        }
    }
}
