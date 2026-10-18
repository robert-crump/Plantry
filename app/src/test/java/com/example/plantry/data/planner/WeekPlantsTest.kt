package com.example.plantry.data.planner

import com.example.plantry.data.CookLog
import com.example.plantry.data.CookLogPlant
import com.example.plantry.data.Ingredient
import com.example.plantry.data.MixComponent
import com.example.plantry.data.Mixes
import com.example.plantry.data.Nutrition
import com.example.plantry.data.PlantPoints
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
    fun theSnapshotsOfThisWeeksEntriesCount() {
        val entries = listOf(entry(1, monday), entry(2, monday.minusDays(1)), entry(3, monday.plusDays(6)))
        val plants = plants(1, 1, 2) + plants(2, 3) + plants(3, 4, 1)
        val week = WeekPlants.of(today = friday, entries = entries, plants = plants, ingredients = ingredients, contents = emptyMap())

        // Last Sunday's entry is in the week before; 1 counts once.
        assertEquals(setOf(1L, 2L, 4L), week.plants.mapNotNull { it.ingredientId }.toSet())
        assertEquals(3.0, week.points, 1e-9)
    }

    @Test
    fun points_areTheSnapshots_notTheCurrentOnes() {
        // 9 is ¼ now, but was logged at 1.
        val week = WeekPlants.of(friday, listOf(entry(1, monday)), plants(1, 9).map { it.copy(plantPoints = 1.0) }, ingredients, emptyMap())

        assertEquals(1.0, week.points, 1e-9)
    }

    @Test
    fun newPoints_resolveMixes_atCurrentPoints() {
        // 6 is a mix of 1 and 2; 7 a mix of 2 and 3.
        val contents = Mixes.contents(listOf(MixComponent(6, 1), MixComponent(6, 2), MixComponent(7, 2), MixComponent(7, 3)))
        val week = WeekPlants.of(friday, listOf(entry(1, monday)), plants(1, 1, 2), ingredients, contents)

        // Of the mix 7 only 3 is new, 1 is in already; 9 adds its quarter.
        assertEquals(1.25, week.newPoints(listOf(7L, 1L, 9L)), 1e-9)
    }

    @Test
    fun deletedIngredients_stillCount_butAreNeverInARecipe() {
        val plants = plants(1, 1).map { it.copy(ingredientId = null) }
        val week = WeekPlants.of(friday, listOf(entry(1, monday)), plants, ingredients, emptyMap())

        assertEquals(1.0, week.points, 1e-9)
        assertEquals(1.0, week.newPoints(listOf(1L)), 1e-9)
    }

    @Test
    fun goalReachedFrom30() {
        val many = (1L..30L).associateWith { ingredient(it, PlantPoints.ONE) }
        fun plants(count: Long) = (1L..count).map { CookLogPlant(cookLogId = 1, ingredientId = it, name = "Zutat $it", plantPoints = 1.0) }

        assertFalse(WeekPlants(plants(29), many, emptyMap()).goalReached)
        assertTrue(WeekPlants(plants(30), many, emptyMap()).goalReached)
    }

    @Test
    fun goalReachedWith_onlyWhenTheRecipeTakesTheWeekThere() {
        assertTrue(plantPointsGoalReachedWith(26.0, 4.0))
        assertFalse(plantPointsGoalReachedWith(26.0, 3.0))
        // Rounded as shown: 29.5 is "30/30".
        assertTrue(plantPointsGoalReachedWith(25.75, 3.75))
        // Already there, nothing left to reach.
        assertFalse(plantPointsGoalReachedWith(30.0, 2.0))
    }

    /** As logged, at the ingredients' current points. */
    private fun plants(cookLogId: Long, vararg ingredientIds: Long) = ingredientIds.map {
        CookLogPlant(cookLogId = cookLogId, ingredientId = it, name = "Zutat $it", plantPoints = ingredients.getValue(it).plantPoints.value)
    }

    private fun entry(id: Long, cookedOn: LocalDate) =
        CookLog(id = id, recipeId = 10, cookedOn = cookedOn, title = "", stats = RecipeStats(99.0, 0.0, 0.0))

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
