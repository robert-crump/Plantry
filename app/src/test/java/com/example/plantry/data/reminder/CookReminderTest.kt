package com.example.plantry.data.reminder

import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CookReminderTest {

    private val today = LocalDate.of(2026, 10, 4)
    private val time = LocalTime.of(19, 30)

    private fun recipe(id: Long, title: String) =
        Recipe(id, title, "", null, bookServings = 2, ourServings = 2, cookingTimeMinutes = null)

    private val dal = recipe(1, "Linsen-Dal")
    private val curry = recipe(2, "Curry")

    @Test
    fun nextAt_plannedBeforeTheTime_isThisEvening() {
        assertEquals(today.atTime(time), CookReminder.nextAt(today.atTime(12, 0), time, enabled = true, anythingPlanned = true))
    }

    @Test
    fun nextAt_atOrAfterTheTime_isTomorrowEvening() {
        val tomorrow = today.plusDays(1).atTime(time)
        assertEquals(tomorrow, CookReminder.nextAt(today.atTime(time), time, enabled = true, anythingPlanned = true))
        assertEquals(tomorrow, CookReminder.nextAt(today.atTime(23, 59), time, enabled = true, anythingPlanned = true))
    }

    @Test
    fun nextAt_offOrNothingPlanned_isNone() {
        assertNull(CookReminder.nextAt(today.atTime(12, 0), time, enabled = false, anythingPlanned = true))
        assertNull(CookReminder.nextAt(today.atTime(12, 0), time, enabled = true, anythingPlanned = false))
    }

    @Test
    fun content_oneRecipe_asksAboutIt() {
        assertEquals(
            ReminderContent.Single(1, "Linsen-Dal"),
            CookReminder.content(true, listOf(PlannedRecipe(1, today)), listOf(dal, curry)),
        )
    }

    @Test
    fun content_severalRecipes_listsTitlesInPlanOrder() {
        val planned = listOf(PlannedRecipe(2, today.minusDays(1)), PlannedRecipe(1, today))

        assertEquals(ReminderContent.Several(listOf("Curry", "Linsen-Dal")), CookReminder.content(true, planned, listOf(dal, curry)))
    }

    @Test
    fun content_missingRecipesAreSkipped() {
        val planned = listOf(PlannedRecipe(1, today), PlannedRecipe(9, today))

        assertEquals(ReminderContent.Single(1, "Linsen-Dal"), CookReminder.content(true, planned, listOf(dal, curry)))
    }

    @Test
    fun content_nothingPlannedOrOff_isNone() {
        assertNull(CookReminder.content(true, emptyList(), listOf(dal)))
        assertNull(CookReminder.content(false, listOf(PlannedRecipe(1, today)), listOf(dal)))
        assertNull(CookReminder.content(true, listOf(PlannedRecipe(9, today)), listOf(dal)))
    }
}
