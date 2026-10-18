package com.example.plantry.data.planner

import com.example.plantry.data.CookLogPlant
import com.example.plantry.data.Dish
import com.example.plantry.data.Nutrition
import com.example.plantry.data.Recipe
import com.example.plantry.data.LineUnit
import com.example.plantry.data.RecipeLine
import com.example.plantry.data.ingredient
import com.example.plantry.data.PlantPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecipeSuggesterTest {

    private val today = LocalDate.of(2026, 10, 2)
    private val tofu = ingredient(1, "Tofu", Nutrition(protein = 15.0))

    private fun recipe(id: Long, servings: Int = 2) = Recipe(
        id = id, title = "Rezept $id", source = "", page = null,
        servings = servings, totalTimeMinutes = 20,
    )

    private fun line(recipeId: Long, grams: Double) =
        RecipeLine(recipeId = recipeId, position = 0, amount = grams, unit = LineUnit.Grams, ingredientId = tofu.id)

    @Test
    fun candidates_proteinPerPortionAndDaysSinceCooked() {
        val candidates = RecipeSuggester.candidates(
            recipes = listOf(recipe(1, servings = 2), recipe(2)),
            lines = listOf(line(1, 400.0)),
            ingredients = listOf(tofu),
            lastCooked = mapOf(1L to today.minusDays(9)),
            today = today,
        )

        assertEquals(
            listOf(
                Candidate(1, daysSinceLastCooked = 9, proteinPerPortion = 30.0),
                // No lines: no known protein; never cooked.
                Candidate(2, daysSinceLastCooked = null, proteinPerPortion = 0.0),
            ),
            candidates,
        )
    }

    @Test
    fun candidates_leaveOutSnacksAndBreakfasts() {
        val candidates = RecipeSuggester.candidates(
            recipes = listOf(recipe(1), recipe(2).copy(dish = Dish.SNACK), recipe(3).copy(dish = Dish.BREAKFAST)),
            lines = emptyList(),
            ingredients = listOf(tofu),
            lastCooked = emptyMap(),
            today = today,
        )

        assertEquals(listOf(1L), candidates.map { it.recipeId })
    }

    @Test
    fun candidates_newPlantPointsAgainstThisWeek() {
        val spinach = ingredient(2, "Spinat").copy(plantPoints = PlantPoints.ONE)
        val beans = ingredient(3, "Bohnen").copy(plantPoints = PlantPoints.ONE)
        val ingredients = listOf(tofu, spinach, beans)
        val week = WeekPlants(listOf(CookLogPlant(ingredientId = spinach.id, name = "Spinat", plantPoints = 1.0)), ingredients.associateBy { it.id }, emptyMap())
        val lines = listOf(
            line(1, 100.0).copy(ingredientId = spinach.id),
            line(1, 100.0).copy(position = 1, ingredientId = beans.id),
            line(2, 100.0).copy(ingredientId = spinach.id),
        )

        val candidates = RecipeSuggester.candidates(listOf(recipe(1), recipe(2)), lines, ingredients, emptyMap(), today, week)

        assertEquals(listOf(1.0, 0.0), candidates.map { it.newPlantPoints })
    }

    @Test
    fun candidates_boostDishesAreBreakfastsAndSnacks() {
        val candidates = RecipeSuggester.candidates(
            recipes = listOf(recipe(1), recipe(2).copy(dish = Dish.SNACK), recipe(3).copy(dish = Dish.BREAKFAST)),
            lines = emptyList(),
            ingredients = listOf(tofu),
            lastCooked = emptyMap(),
            today = today,
            dishes = RecipeSuggester.BOOST_DISHES,
        )

        assertEquals(listOf(2L, 3L), candidates.map { it.recipeId })
    }

    @Test
    fun boostCandidates_onlyThoseAddingPlantPoints() {
        val candidates = listOf(Candidate(1, null, 0.0, newPlantPoints = 0.0), Candidate(2, null, 0.0, newPlantPoints = 0.25))

        assertEquals(listOf(2L), RecipeSuggester.boostCandidates(candidates).map { it.recipeId })
        assertTrue(RecipeSuggester.boostCandidates(candidates.take(1)).isEmpty())
    }

    @Test
    fun boostWanted_fridayToSundayUnderTheGoal() {
        val thursday = LocalDate.of(2026, 10, 15)
        val ingredients = (1L..30L).associateWith { ingredient(it, "Zutat $it").copy(plantPoints = PlantPoints.ONE) }
        fun plants(count: Long) = (1L..count).map { CookLogPlant(ingredientId = it, name = "Zutat $it", plantPoints = 1.0) }
        val under = WeekPlants(plants(29), ingredients, emptyMap())
        val reached = WeekPlants(plants(30), ingredients, emptyMap())

        assertFalse(RecipeSuggester.boostWanted(thursday, under))
        assertTrue(RecipeSuggester.boostWanted(thursday.plusDays(1), under))
        assertTrue(RecipeSuggester.boostWanted(thursday.plusDays(3), under))
        assertFalse(RecipeSuggester.boostWanted(thursday.plusDays(4), under))
        assertFalse(RecipeSuggester.boostWanted(thursday.plusDays(1), reached))
    }
}
