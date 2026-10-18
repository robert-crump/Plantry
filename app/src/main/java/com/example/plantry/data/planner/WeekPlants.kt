package com.example.plantry.data.planner

import com.example.plantry.data.CookLog
import com.example.plantry.data.CookLogPlant
import com.example.plantry.data.Ingredient
import com.example.plantry.data.MixContents
import com.example.plantry.data.Mixes
import com.example.plantry.data.distinctPlantPoints
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** The plant points to aim for in a week, shown as e.g. "23/30" in the history's week headers. */
const val WeeklyPlantPointsGoal = 30

/** Whether [points], rounded as shown, reach [WeeklyPlantPointsGoal]. */
fun plantPointsGoalReached(points: Double): Boolean = points.roundToInt() >= WeeklyPlantPointsGoal

/** Whether a recipe adding [newPoints] would take a week at [weekPoints] to the goal it hasn't reached yet. */
fun plantPointsGoalReachedWith(weekPoints: Double, newPoints: Double): Boolean =
    !plantPointsGoalReached(weekPoints) && plantPointsGoalReached(weekPoints + newPoints)

/**
 * The distinct plants of this Monday–Sunday week: those its cook-log entries snapshotted (see
 * [CookLogPlant]), so the points match the history's week header. Planned recipes don't count
 * until they are cooked.
 */
class WeekPlants(
    val plants: List<CookLogPlant>,
    private val ingredients: Map<Long, Ingredient>,
    private val contents: MixContents,
) {
    /** The week's plant points so far, by the points each plant was logged with. */
    val points: Double get() = distinctPlantPoints(plants)

    val goalReached: Boolean get() = plantPointsGoalReached(points)

    private val ingredientIds = plants.mapNotNullTo(mutableSetOf()) { it.ingredientId }

    /**
     * The plant points [ingredientIds] would add, by their current points: those of their plants
     * (mixes resolved, see [Mixes.plants]) not yet in this week.
     */
    fun newPoints(ingredientIds: Iterable<Long>): Double =
        (Mixes.plants(ingredientIds, contents) - this.ingredientIds).sumOf { ingredients[it]?.plantPoints?.value ?: 0.0 }

    companion object {
        fun of(
            today: LocalDate,
            entries: List<CookLog>,
            plants: List<CookLogPlant>,
            ingredients: Map<Long, Ingredient>,
            contents: MixContents,
        ): WeekPlants {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val week = monday..monday.plusDays(6)
            val entryIds = entries.filter { it.cookedOn in week }.mapTo(mutableSetOf()) { it.id }
            return WeekPlants(plants.filter { it.cookLogId in entryIds }, ingredients, contents)
        }
    }
}
