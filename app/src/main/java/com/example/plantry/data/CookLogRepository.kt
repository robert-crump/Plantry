package com.example.plantry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** The cook log, to read; [MealPlan] writes it. */
class CookLogRepository(private val dao: CookLogDao) {
    /** Newest first. */
    fun observeHistory(): Flow<List<CookLog>> = dao.observeHistory()

    /** The plants of every entry, see [CookLogPlant]. */
    fun observePlants(): Flow<List<CookLogPlant>> = dao.observePlants()

    /** The latest cook date per recipe; recipes never cooked are missing. */
    fun observeLastCooked(): Flow<Map<Long, LocalDate>> =
        dao.observeLastCooked().map { all -> all.associate { it.recipeId to it.lastCookedOn } }

    /** [today] is read on every emission, so the stats stay right across midnight. */
    fun observeStats(recipeId: Long, today: () -> LocalDate): Flow<CookingStats> =
        dao.observeDates(recipeId).map { CookingStats.from(it, today()) }
}
