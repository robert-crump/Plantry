package com.example.plantry.data.reminder

import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.Recipe
import java.time.LocalDateTime
import java.time.LocalTime

/** What the daily reminder asks. */
sealed interface ReminderContent {
    /** "Habt ihr heute <Titel> gekocht?" with Ja / Nein. */
    data class Single(val recipeId: Long, val title: String) : ReminderContent

    /** "Habt ihr heute etwas von Geplant gekocht?" with the titles, oldest plan first. */
    data class Several(val titles: List<String>) : ReminderContent
}

/** The rules of the "did you cook it?" reminder; the alarm and the notification only carry them out. */
object CookReminder {

    /**
     * When the next reminder is due: today at [time] if that is still ahead of [now], otherwise
     * tomorrow. Null when there is nothing to ask, i.e. the reminder is off or Geplant is empty.
     */
    fun nextAt(now: LocalDateTime, time: LocalTime, enabled: Boolean, anythingPlanned: Boolean): LocalDateTime? {
        if (!enabled || !anythingPlanned) return null
        val today = now.toLocalDate().atTime(time)
        return if (now < today) today else today.plusDays(1)
    }

    /**
     * What to show for [planned] (as [PlannedRepository.observe] returns it); recipes that no
     * longer exist are skipped. Null when nothing should be shown.
     */
    fun content(enabled: Boolean, planned: List<PlannedRecipe>, recipes: List<Recipe>): ReminderContent? {
        if (!enabled) return null
        val recipesById = recipes.associateBy { it.id }
        val shown = planned.mapNotNull { recipesById[it.recipeId] }
        return when (shown.size) {
            0 -> null
            1 -> ReminderContent.Single(shown.single().id, shown.single().title)
            else -> ReminderContent.Several(shown.map { it.title })
        }
    }
}
