package com.example.plantry.data.planner

import com.example.plantry.data.CookLog
import com.example.plantry.data.Ingredient
import com.example.plantry.data.LineUnit
import com.example.plantry.data.MixComponent
import com.example.plantry.data.Mixes
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.PlantPoints
import com.example.plantry.data.RecipeLine
import com.example.plantry.data.RecipeStats
import com.example.plantry.data.StoreSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekPlantsTest {

    private val monday = LocalDate.of(2026, 10, 12)
    private val friday = monday.plusDays(4)
    private val ingredients = (1L..8L).associateWith { ingredient(it, PlantPoints.ONE) } +
        mapOf(9L to ingredient(9, PlantPoints.QUARTER))

    @Test
    fun loggedAndPlannedRecipesOfThisWeekCount() {
        val lines = lines(10, 1, 2) + lines(11, 3) + lines(12, 4) + lines(13, 5)
        val week = WeekPlants.of(
            today = friday,
            entries = listOf(entry(10, monday), entry(11, monday.minusDays(1))),
            planned = listOf(PlannedRecipe(12, monday.plusDays(6)), PlannedRecipe(13, monday.plusWeeks(1))),
            lines = lines,
            ingredients = ingredients,
            contents = emptyMap(),
        )

        // Last Sunday's entry and next Monday's plan are in other weeks.
        assertEquals(setOf(1L, 2L, 4L), week.plants)
        assertEquals(3.0, week.points, 1e-9)
    }

    @Test
    fun entriesOfDeletedRecipesAreSkipped() {
        val week = WeekPlants.of(friday, listOf(entry(null, monday)), emptyList(), lines(10, 1), ingredients, emptyMap())

        assertEquals(emptySet<Long>(), week.plants)
    }

    @Test
    fun mixesAreResolvedToTheirPlants() {
        // 6 is a mix of 1 and 2; 7 a mix of 2 and 3.
        val contents = Mixes.contents(listOf(MixComponent(6, 1), MixComponent(6, 2), MixComponent(7, 2), MixComponent(7, 3)))
        val week = WeekPlants.of(friday, listOf(entry(10, monday)), emptyList(), lines(10, 6), ingredients, contents)

        assertEquals(setOf(1L, 2L), week.plants)
        // Of the mix 7 only 3 is new, 1 is in already; 9 adds its quarter.
        assertEquals(1.25, week.newPoints(listOf(7L, 1L, 9L)), 1e-9)
    }

    @Test
    fun goalReachedFrom30() {
        val many = (1L..30L).associateWith { ingredient(it, PlantPoints.ONE) }

        assertFalse(WeekPlants((1L..29L).toSet(), many, emptyMap()).goalReached)
        assertTrue(WeekPlants((1L..30L).toSet(), many, emptyMap()).goalReached)
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
