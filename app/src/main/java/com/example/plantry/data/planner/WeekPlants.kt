package com.example.plantry.data.planner

import com.example.plantry.data.CookLog
import com.example.plantry.data.Ingredient
import com.example.plantry.data.MixContents
import com.example.plantry.data.Mixes
import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.RecipeLine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** The plant points to aim for in a week, shown as e.g. "23/30" in the history's week headers. */
const val WeeklyPlantPointsGoal = 30

/** Whether [points], rounded as shown, reach [WeeklyPlantPointsGoal]. */
fun plantPointsGoalReached(points: Double): Boolean = points.roundToInt() >= WeeklyPlantPointsGoal

/**
 * The distinct plants of this Monday–Sunday week, each mix resolved to its components (see
 * [Mixes.plants]): those of the week's cook-log entries plus those of the recipes planned for one of
 * its days, by their recipes as they are now.
 */
class WeekPlants(
    val plants: Set<Long>,
    private val ingredients: Map<Long, Ingredient>,
    private val contents: MixContents,
) {
    /** The week's plant points so far. */
    val points: Double get() = plants.sumOf { ingredients[it]?.plantPoints?.value ?: 0.0 }

    val goalReached: Boolean get() = plantPointsGoalReached(points)

    /** The plant points [ingredientIds] would add: those of their plants not yet in this week. */
    fun newPoints(ingredientIds: Iterable<Long>): Double =
        (Mixes.plants(ingredientIds, contents) - plants).sumOf { ingredients[it]?.plantPoints?.value ?: 0.0 }

    companion object {
        fun of(
            today: LocalDate,
            entries: List<CookLog>,
            planned: List<PlannedRecipe>,
            lines: List<RecipeLine>,
            ingredients: Map<Long, Ingredient>,
            contents: MixContents,
        ): WeekPlants {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val week = monday..monday.plusDays(6)
            val recipeIds = entries.filter { it.cookedOn in week }.mapNotNull { it.recipeId } +
                planned.filter { it.plannedOn in week }.map { it.recipeId }
            val ingredientIds = lines.groupBy({ it.recipeId }, { it.ingredientId })
            return WeekPlants(Mixes.plants(recipeIds.flatMap { ingredientIds[it].orEmpty() }, contents), ingredients, contents)
        }
    }
}
