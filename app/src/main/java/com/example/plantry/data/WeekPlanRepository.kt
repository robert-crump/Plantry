package com.example.plantry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** A slot of a week's menu, e.g. to undo marking it done. */
data class SlotRef(val weekStart: LocalDate, val position: Int)

class WeekPlanRepository(private val dao: WeekPlanDao) {

    /** [weekStart] must be a Saturday, see [WeekPlan.startOf]. */
    fun observeWeek(weekStart: LocalDate): Flow<WeekPlan> =
        dao.observeWeek(weekStart).map { WeekPlan.of(weekStart, it) }

    /** How many recipes [rollover] would add: none unless the menu of [weekStart] is still empty. */
    fun observeRolloverCount(weekStart: LocalDate): Flow<Int> =
        combine(dao.observeSlots(weekStart.minusWeeks(1)), dao.observeSlots(weekStart)) { previous, current ->
            if (current.isEmpty()) WeekPlan.rollover(weekStart, previous, current).size else 0
        }

    /** Puts [recipeId] into the slot, replacing what was there; the slot starts not done. */
    suspend fun pick(weekStart: LocalDate, position: Int, recipeId: Long) {
        require(position in 0 until WeekPlan.SLOT_COUNT) { "No slot $position" }
        dao.upsert(WeekPlanSlot(weekStart, position, recipeId))
    }

    suspend fun remove(slot: SlotRef) = dao.delete(slot.weekStart, slot.position)

    suspend fun setDone(slot: SlotRef, done: Boolean) = dao.setDone(slot.weekStart, slot.position, done)

    /** Copies the uncooked recipes of the previous week into the free slots of [weekStart]. */
    suspend fun rollover(weekStart: LocalDate) {
        val slots = WeekPlan.rollover(weekStart, dao.getSlots(weekStart.minusWeeks(1)), dao.getSlots(weekStart))
        dao.insertAll(slots)
    }

    /**
     * Marks [recipeId] done on the menu of the week containing [cookedOn], if it is on it and not
     * done yet. Returns the slot marked, or null if the menu was left unchanged.
     */
    suspend fun markCooked(recipeId: Long, cookedOn: LocalDate): SlotRef? {
        val weekStart = WeekPlan.startOf(cookedOn)
        val slot = dao.getSlots(weekStart).firstOrNull { it.recipeId == recipeId && !it.done } ?: return null
        dao.setDone(weekStart, slot.position, true)
        return SlotRef(weekStart, slot.position)
    }
}
