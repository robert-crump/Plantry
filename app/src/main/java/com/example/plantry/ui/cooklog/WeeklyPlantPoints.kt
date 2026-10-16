package com.example.plantry.ui.cooklog

import com.example.plantry.data.CookLog
import com.example.plantry.data.Ingredient
import com.example.plantry.data.RecipeLine
import java.time.LocalDate
import kotlin.math.roundToInt

/** The plant points to aim for in a week, shown as e.g. "23/30" in the history's week headers. */
const val WeeklyPlantPointsGoal = 30

/**
 * Per Monday–Sunday week, keyed by its Monday: the plant points of the distinct ingredients of the
 * week's entries, by their recipes as they are now. Entries of deleted recipes are skipped.
 */
internal fun weeklyPlantPoints(
    entries: List<CookLog>,
    lines: List<RecipeLine>,
    ingredients: Map<Long, Ingredient>,
): Map<LocalDate, Double> {
    val ingredientIds = lines.groupBy({ it.recipeId }, { it.ingredientId })
    return groupByWeek(entries) { it.cookedOn }.mapValues { (_, week) ->
        week.flatMapTo(mutableSetOf()) { entry -> entry.recipeId?.let { ingredientIds[it] }.orEmpty() }
            .sumOf { ingredients[it]?.plantPoints?.value ?: 0.0 }
    }
}

/** Whether [points], rounded as shown, reach [WeeklyPlantPointsGoal]. */
internal fun plantPointsGoalReached(points: Double): Boolean = points.roundToInt() >= WeeklyPlantPointsGoal
