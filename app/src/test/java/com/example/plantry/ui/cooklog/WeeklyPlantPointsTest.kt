package com.example.plantry.ui.cooklog

import com.example.plantry.data.planner.plantPointsGoalReached
import com.example.plantry.data.CookLog
import com.example.plantry.data.CookLogPlant
import com.example.plantry.data.RecipeStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyPlantPointsTest {

    private val monday = LocalDate.of(2026, 10, 12)

    @Test
    fun aPlantInSeveralDishesOfAWeek_countsOnce() {
        val entries = listOf(entry(1, monday), entry(2, monday.plusDays(2)), entry(3, monday.plusDays(6)))
        val plants = plants(1, 1L to 1.0, 3L to 0.25) + plants(2, 1L to 1.0, 2L to 1.0, 3L to 0.25) + plants(3, 2L to 1.0)

        assertEquals(mapOf(monday to 2.25), weeklyPlantPoints(entries, plants))
    }

    @Test
    fun eachWeekCountsOnItsOwn() {
        val entries = listOf(entry(1, monday), entry(2, monday.minusDays(1)))
        val plants = plants(1, 1L to 1.0, 2L to 1.0) + plants(2, 1L to 1.0, 2L to 1.0)

        assertEquals(mapOf(monday to 2.0, monday.minusWeeks(1) to 2.0), weeklyPlantPoints(entries, plants))
    }

    @Test
    fun aWeekOfEntriesWithoutPlants_hasZero() {
        val entries = listOf(entry(1, monday), entry(2, monday.minusWeeks(1)))

        assertEquals(mapOf(monday to 1.0, monday.minusWeeks(1) to 0.0), weeklyPlantPoints(entries, plants(1, 1L to 1.0)))
    }

    @Test
    fun deletedIngredients_matchByName() {
        val entries = listOf(entry(1, monday), entry(2, monday.plusDays(1)))
        val plants = listOf(
            CookLogPlant(cookLogId = 1, ingredientId = null, name = "Zwiebel", plantPoints = 1.0),
            CookLogPlant(cookLogId = 2, ingredientId = null, name = "Zwiebel", plantPoints = 1.0),
            CookLogPlant(cookLogId = 2, ingredientId = null, name = "Lauch", plantPoints = 1.0),
        )

        assertEquals(mapOf(monday to 2.0), weeklyPlantPoints(entries, plants))
    }

    @Test
    fun theSnapshotsPointsCount_theHighestForAPlantLoggedWithDifferentOnes() {
        // Ingredient 1 was ¼ on Monday and 1 by Wednesday.
        val entries = listOf(entry(1, monday), entry(2, monday.plusDays(2)))

        assertEquals(mapOf(monday to 1.25), weeklyPlantPoints(entries, plants(1, 1L to 0.25, 2L to 0.25) + plants(2, 1L to 1.0)))
    }

    @Test
    fun goal_isReachedByTheRoundedPoints() {
        assertFalse(plantPointsGoalReached(29.25))
        assertTrue(plantPointsGoalReached(29.5))
        assertTrue(plantPointsGoalReached(35.0))
    }

    private fun plants(cookLogId: Long, vararg points: Pair<Long, Double>) = points.map { (id, value) ->
        CookLogPlant(cookLogId = cookLogId, ingredientId = id, name = "Zutat $id", plantPoints = value)
    }

    private fun entry(id: Long, cookedOn: LocalDate) =
        CookLog(id = id, recipeId = 10, cookedOn = cookedOn, title = "", stats = RecipeStats(99.0, 0.0, 0.0))
}
