package com.example.plantry.data.planner

import com.example.plantry.data.Nutrition
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.ingredient
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekPlannerTest {

    private val today = LocalDate.of(2026, 10, 2)
    private val tofu = ingredient(1, "Tofu", Nutrition(protein = 15.0))

    private fun recipe(id: Long, servings: Int = 2) = Recipe(
        id = id, title = "Rezept $id", source = "", page = null,
        bookServings = servings, ourServings = servings, cookingTimeMinutes = 20,
    )

    private fun line(recipeId: Long, grams: Double) =
        RecipeIngredient(recipeId = recipeId, position = 0, originalText = "", grams = grams, ingredientId = tofu.id)

    @Test
    fun candidates_proteinPerPortionAndDaysSinceCooked() {
        val candidates = WeekPlanner.candidates(
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
}
