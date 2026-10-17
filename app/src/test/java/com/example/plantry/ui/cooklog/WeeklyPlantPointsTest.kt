package com.example.plantry.ui.cooklog

import com.example.plantry.data.planner.plantPointsGoalReached
import com.example.plantry.data.CookLog
import com.example.plantry.data.Ingredient
import com.example.plantry.data.MixComponent
import com.example.plantry.data.Mixes
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.LineUnit
import com.example.plantry.data.RecipeLine
import com.example.plantry.data.RecipeStats
import com.example.plantry.data.StoreSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyPlantPointsTest {

    private val monday = LocalDate.of(2026, 10, 12)
    private val ingredients = listOf(
        ingredient(1, PlantPoints.ONE),
        ingredient(2, PlantPoints.ONE),
        ingredient(3, PlantPoints.QUARTER),
        ingredient(4, PlantPoints.ZERO),
    ).associateBy { it.id }

    @Test
    fun anIngredientInSeveralDishesOfAWeek_countsOnce() {
        val lines = lines(10, 1, 3, 4) + lines(11, 1, 2, 3)
        val entries = listOf(entry(11, monday.plusDays(2)), entry(10, monday), entry(10, monday.plusDays(6)))

        assertEquals(mapOf(monday to 2.25), weeklyPlantPoints(entries, lines, ingredients))
    }

    @Test
    fun eachWeekCountsOnItsOwn() {
        val lines = lines(10, 1, 2)
        val entries = listOf(entry(10, monday), entry(10, monday.minusDays(1)))

        assertEquals(mapOf(monday to 2.0, monday.minusWeeks(1) to 2.0), weeklyPlantPoints(entries, lines, ingredients))
    }

    @Test
    fun entriesOfDeletedRecipes_areSkipped_andAWeekOfOnlyThoseHasZero() {
        val lines = lines(10, 1)
        val entries = listOf(entry(10, monday), entry(null, monday), entry(null, monday.minusWeeks(1)))

        assertEquals(mapOf(monday to 1.0, monday.minusWeeks(1) to 0.0), weeklyPlantPoints(entries, lines, ingredients))
    }

    @Test
    fun mixes_countEachOfTheirPlantsOnceWithTheWeeksOtherIngredients() {
        // 5 is a mix of 1 and 2 (its own point is ignored), 6 a mix of 2 and 3.
        val withMixes = ingredients + listOf(ingredient(5, PlantPoints.ONE), ingredient(6, PlantPoints.ZERO)).associateBy { it.id }
        val contents = Mixes.contents(listOf(MixComponent(5, 1), MixComponent(5, 2), MixComponent(6, 2), MixComponent(6, 3)))
        val lines = lines(10, 1, 5) + lines(11, 6)
        val entries = listOf(entry(10, monday), entry(11, monday.plusDays(1)))

        assertEquals(mapOf(monday to 2.25), weeklyPlantPoints(entries, lines, withMixes, contents))
    }

    @Test
    fun goal_isReachedByTheRoundedPoints() {
        assertFalse(plantPointsGoalReached(29.25))
        assertTrue(plantPointsGoalReached(29.5))
        assertTrue(plantPointsGoalReached(35.0))
    }

    private fun lines(recipeId: Long, vararg ingredientIds: Long) = ingredientIds.mapIndexed { position, id ->
        RecipeLine(recipeId = recipeId, position = position, amount = 100.0, unit = LineUnit.Grams, ingredientId = id)
    }

    private fun entry(recipeId: Long?, cookedOn: LocalDate) =
        CookLog(recipeId = recipeId, cookedOn = cookedOn, title = "", stats = RecipeStats(99.0, 0.0, 0.0))

    private fun ingredient(id: Long, points: PlantPoints) = Ingredient(
        id = id,
        name = "Zutat $id",
        fdcId = null,
        usdaDescription = null,
        nutrition = Nutrition(),
        storeSection = StoreSection.OTHER,
        plantPoints = points,
    )
}
