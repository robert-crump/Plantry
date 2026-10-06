package com.example.plantry.data.reminder

import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.Recipe
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What the daily reminder asks. */
sealed interface ReminderContent {
    /** "Habt ihr heute <Titel> gekocht?" with Ja / Nein. */
    data class Single(val recipeId: Long, val title: String) : ReminderContent

    /** "Habt ihr heute etwas von Geplant gekocht?" with the titles in Geplant order. */
    data class Several(val titles: List<String>) : ReminderContent
}

/** The rules of the "did you cook it?" reminder; the alarm and the notification only carry them out. */
object CookReminder {

    /**
     * When the next reminder is due: at [time] on the first day in [plannedDays] whose reminder
     * is still ahead of [now]. Null when there is nothing to ask, i.e. the reminder is off or
     * nothing is planned for today (before [time]) or later.
     */
    fun nextAt(now: LocalDateTime, time: LocalTime, enabled: Boolean, plannedDays: Collection<LocalDate>): LocalDateTime? {
        if (!enabled) return null
        return plannedDays.map { it.atTime(time) }.filter { it > now }.minOrNull()
    }

    /**
     * What to show on [today] for [planned] (as [PlannedRepository.observe] returns it): only the
     * recipes planned for that day; recipes that no longer exist are skipped. Null when nothing
     * should be shown.
     */
    fun content(enabled: Boolean, today: LocalDate, planned: List<PlannedRecipe>, recipes: List<Recipe>): ReminderContent? {
        if (!enabled) return null
        val recipesById = recipes.associateBy { it.id }
        val shown = planned.filter { it.plannedOn == today }.mapNotNull { recipesById[it.recipeId] }
        return when (shown.size) {
            0 -> null
            1 -> ReminderContent.Single(shown.single().id, shown.single().title)
            else -> ReminderContent.Several(shown.map { it.title })
        }
    }
}
