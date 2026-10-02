package com.example.plantry.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * One filled slot of a week's menu. Empty slots have no row; deleting the recipe empties its slot.
 */
@Entity(
    tableName = "week_plan_slots",
    primaryKeys = ["weekStart", "position"],
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId")],
)
data class WeekPlanSlot(
    /** Always a Saturday, see [WeekPlan.startOf]. */
    val weekStart: LocalDate,
    /** 0 until [WeekPlan.SLOT_COUNT] - 1. */
    val position: Int,
    val recipeId: Long,
    val done: Boolean = false,
)

/** A [WeekPlanSlot] with the title of its recipe. */
data class PlannedRecipe(
    @Embedded val slot: WeekPlanSlot,
    val recipeTitle: String,
)

/** The menu of a week: [WeekPlan.SLOT_COUNT] recipes without fixed days; null slots are empty. */
data class WeekPlan(
    val weekStart: LocalDate,
    val slots: List<PlannedRecipe?>,
) {
    val recipeIds: Set<Long> get() = slots.mapNotNullTo(mutableSetOf()) { it?.slot?.recipeId }

    companion object {
        const val SLOT_COUNT = 5

        /** Weeks start on Saturday, the shopping day. */
        val FIRST_DAY: DayOfWeek = DayOfWeek.SATURDAY

        /** The Saturday on or before [date]. */
        fun startOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(FIRST_DAY))

        fun of(weekStart: LocalDate, planned: List<PlannedRecipe>): WeekPlan {
            val byPosition = planned.associateBy { it.slot.position }
            return WeekPlan(weekStart, List(SLOT_COUNT) { byPosition[it] })
        }

        /**
         * The slots to add to [current] when rolling over the uncooked recipes of [previous]:
         * in their old order, skipping recipes already on the menu, into the free positions.
         */
        fun rollover(weekStart: LocalDate, previous: List<WeekPlanSlot>, current: List<WeekPlanSlot>): List<WeekPlanSlot> {
            val taken = current.mapTo(mutableSetOf()) { it.position }
            val onMenu = current.mapTo(mutableSetOf()) { it.recipeId }
            val uncooked = previous.sortedBy { it.position }
                .filter { !it.done && it.recipeId !in onMenu }
                .map { it.recipeId }
                .distinct()
            val free = (0 until SLOT_COUNT).filter { it !in taken }
            return uncooked.zip(free) { recipeId, position -> WeekPlanSlot(weekStart, position, recipeId) }
        }
    }
}
