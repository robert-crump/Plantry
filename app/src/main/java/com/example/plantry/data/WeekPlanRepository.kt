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
     * Fills the empty slots of [weekStart] in order with what [suggest] returns for the recipes
     * already on the menu and the number of empty slots; filled slots are never touched.
     * Returns how many slots were filled.
     */
    suspend fun fillEmpty(weekStart: LocalDate, suggest: (onMenu: Set<Long>, count: Int) -> List<Long>): Int {
        val current = dao.getSlots(weekStart)
        val free = WeekPlan.freePositions(current)
        if (free.isEmpty()) return 0
        val onMenu = current.mapTo(mutableSetOf()) { it.recipeId }
        val slots = suggest(onMenu, free.size).filter { it !in onMenu }.distinct()
            .zip(free) { recipeId, position -> WeekPlanSlot(weekStart, position, recipeId) }
        dao.insertAll(slots)
        return slots.size
    }

    /**
     * Replaces the recipe in [slot] with what [suggest] returns for the recipes on the menu
     * (including the one replaced); the slot starts not done. Returns false, leaving the menu
     * unchanged, if the slot is empty or there is no suggestion.
     */
    suspend fun swap(slot: SlotRef, suggest: (onMenu: Set<Long>) -> Long?): Boolean {
        val current = dao.getSlots(slot.weekStart)
        if (current.none { it.position == slot.position }) return false
        val onMenu = current.mapTo(mutableSetOf()) { it.recipeId }
        val recipeId = suggest(onMenu)?.takeIf { it !in onMenu } ?: return false
        dao.upsert(WeekPlanSlot(slot.weekStart, slot.position, recipeId))
        return true
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
