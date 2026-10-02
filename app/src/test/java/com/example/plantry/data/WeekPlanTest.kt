package com.example.plantry.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekPlanTest {

    private val saturday = LocalDate.of(2026, 10, 3)
    private val lastSaturday = saturday.minusWeeks(1)

    @Test
    fun startOf_saturdayIsItsOwnWeekStart() {
        assertEquals(saturday, WeekPlan.startOf(saturday))
    }

    @Test
    fun startOf_fridayBelongsToThePreviousWeek() {
        assertEquals(lastSaturday, WeekPlan.startOf(LocalDate.of(2026, 10, 2)))
    }

    @Test
    fun startOf_everyDayUntilFridayMapsToTheSameSaturday() {
        (0L..6L).forEach { offset ->
            assertEquals(saturday, WeekPlan.startOf(saturday.plusDays(offset)))
        }
        assertEquals(saturday.plusWeeks(1), WeekPlan.startOf(saturday.plusDays(7)))
    }

    @Test
    fun startOf_acrossYearBoundary() {
        // Friday 1 January 2027 still belongs to the week starting Saturday 26 December 2026.
        assertEquals(LocalDate.of(2026, 12, 26), WeekPlan.startOf(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun of_placesSlotsByPositionAndLeavesGapsEmpty() {
        val planned = listOf(planned(position = 3, recipeId = 7), planned(position = 0, recipeId = 9))

        val plan = WeekPlan.of(saturday, planned)

        assertEquals(listOf(9L, null, null, 7L, null), plan.slots.map { it?.slot?.recipeId })
        assertEquals(setOf(7L, 9L), plan.recipeIds)
    }

    @Test
    fun rollover_copiesOnlyUncookedRecipesIntoEmptyWeek() {
        val previous = listOf(
            WeekPlanSlot(lastSaturday, 0, recipeId = 1, done = true),
            WeekPlanSlot(lastSaturday, 2, recipeId = 2),
            WeekPlanSlot(lastSaturday, 4, recipeId = 3),
        )

        val added = WeekPlan.rollover(saturday, previous, current = emptyList())

        assertEquals(listOf(WeekPlanSlot(saturday, 0, 2), WeekPlanSlot(saturday, 1, 3)), added)
    }

    @Test
    fun rollover_fillsFreePositionsAndSkipsRecipesAlreadyOnTheMenu() {
        val previous = (0..4).map { WeekPlanSlot(lastSaturday, it, recipeId = it + 1L) }
        val current = listOf(WeekPlanSlot(saturday, 0, recipeId = 2), WeekPlanSlot(saturday, 2, recipeId = 9))

        val added = WeekPlan.rollover(saturday, previous, current)

        assertEquals(
            listOf(WeekPlanSlot(saturday, 1, 1), WeekPlanSlot(saturday, 3, 3), WeekPlanSlot(saturday, 4, 4)),
            added,
        )
    }

    @Test
    fun rollover_nothingWhenEverythingWasCooked() {
        val previous = listOf(WeekPlanSlot(lastSaturday, 0, recipeId = 1, done = true))

        assertEquals(emptyList<WeekPlanSlot>(), WeekPlan.rollover(saturday, previous, emptyList()))
    }

    private fun planned(position: Int, recipeId: Long) =
        PlannedRecipe(WeekPlanSlot(saturday, position, recipeId), recipeTitle = "Rezept $recipeId")
}
