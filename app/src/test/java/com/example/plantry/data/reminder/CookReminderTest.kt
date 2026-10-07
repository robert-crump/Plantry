package com.example.plantry.data.reminder

import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun nextAt_plannedForTodayBeforeTheTime_isThisEvening() {
        assertEquals(today.atTime(time), CookReminder.nextAt(today.atTime(12, 0), time, enabled = true, plannedDays = listOf(today)))
    }

    @Test
    fun nextAt_isTheEveningOfTheFirstPlannedDayStillAhead() {
        val days = listOf(today.plusDays(5), today, today.plusDays(2), today.minusDays(1))

        assertEquals(today.atTime(time), CookReminder.nextAt(today.atTime(12, 0), time, enabled = true, plannedDays = days))
        // Today's reminder is due or past: the next planned day, skipping the empty one in between.
        assertEquals(today.plusDays(2).atTime(time), CookReminder.nextAt(today.atTime(time), time, enabled = true, plannedDays = days))
        assertEquals(today.plusDays(2).atTime(time), CookReminder.nextAt(today.atTime(23, 59), time, enabled = true, plannedDays = days))
    }

    @Test
    fun nextAt_offOrNothingPlannedFromTodayOn_isNone() {
        assertNull(CookReminder.nextAt(today.atTime(12, 0), time, enabled = false, plannedDays = listOf(today)))
        assertNull(CookReminder.nextAt(today.atTime(12, 0), time, enabled = true, plannedDays = emptyList()))
        assertNull(CookReminder.nextAt(today.atTime(12, 0), time, enabled = true, plannedDays = listOf(today.minusDays(1))))
        assertNull(CookReminder.nextAt(today.atTime(20, 0), time, enabled = true, plannedDays = listOf(today)))
    }

    @Test
    fun content_oneRecipeForToday_asksAboutIt() {
        val planned = listOf(PlannedRecipe(2, today.minusDays(1)), PlannedRecipe(1, today), PlannedRecipe(3, today.plusDays(1)))

        assertEquals(ReminderContent.Single(1, "Linsen-Dal"), CookReminder.content(true, today, planned, listOf(dal, curry)))
    }

    @Test
    fun content_severalRecipesForToday_listsTitlesInPlanOrder() {
        val planned = listOf(PlannedRecipe(2, today), PlannedRecipe(1, today))

        assertEquals(ReminderContent.Several(2, listOf("Curry", "Linsen-Dal")), CookReminder.content(true, today, planned, listOf(dal, curry)))
    }

    @Test
    fun content_missingRecipesAreSkipped() {
        val planned = listOf(PlannedRecipe(1, today), PlannedRecipe(9, today))

        assertEquals(ReminderContent.Single(1, "Linsen-Dal"), CookReminder.content(true, today, planned, listOf(dal, curry)))
    }

    @Test
    fun content_nothingPlannedForTodayOrOff_isNone() {
        assertNull(CookReminder.content(true, today, emptyList(), listOf(dal)))
        assertNull(CookReminder.content(true, today, listOf(PlannedRecipe(1, today.minusDays(1)), PlannedRecipe(2, today.plusDays(1))), listOf(dal, curry)))
        assertNull(CookReminder.content(false, today, listOf(PlannedRecipe(1, today)), listOf(dal)))
        assertNull(CookReminder.content(true, today, listOf(PlannedRecipe(9, today)), listOf(dal)))
    }

    @Test
    fun nextDailyAt_isTheNextTimeTodayOrTomorrow() {
        val morning = LocalTime.of(7, 0)

        assertEquals(today.atTime(morning), CookReminder.nextDailyAt(today.atTime(6, 59), morning, enabled = true))
        assertEquals(today.plusDays(1).atTime(morning), CookReminder.nextDailyAt(today.atTime(morning), morning, enabled = true))
        assertEquals(today.plusDays(1).atTime(morning), CookReminder.nextDailyAt(today.atTime(23, 59), morning, enabled = true))
        assertNull(CookReminder.nextDailyAt(today.atTime(6, 0), morning, enabled = false))
    }

    @Test
    fun proposalWanted_onlyWhenOnAndNothingPlannedForToday() {
        val laterOnly = listOf(PlannedRecipe(1, today.minusDays(1)), PlannedRecipe(2, today.plusDays(1)))

        assertTrue(CookReminder.proposalWanted(true, today, emptyList()))
        assertTrue(CookReminder.proposalWanted(true, today, laterOnly))
        assertFalse(CookReminder.proposalWanted(true, today, laterOnly + PlannedRecipe(3, today)))
        assertFalse(CookReminder.proposalWanted(false, today, emptyList()))
    }
}
