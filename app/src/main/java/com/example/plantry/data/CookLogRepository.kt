package com.example.plantry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class CookLogRepository(private val dao: CookLogDao) {

    fun observeHistory(): Flow<List<CookLogEntry>> = dao.observeHistory()

    /** The latest cook date per recipe; recipes never cooked are missing. */
    fun observeLastCooked(): Flow<Map<Long, LocalDate>> =
        dao.observeLastCooked().map { all -> all.associate { it.recipeId to it.lastCookedOn } }

    /** [today] is read on every emission, so the stats stay right across midnight. */
    fun observeStats(recipeId: Long, today: () -> LocalDate): Flow<CookingStats> =
        dao.observeDates(recipeId).map { CookingStats.from(it, today()) }

    /** Logs [recipeId] as cooked on [date] and returns the entry id, e.g. for undo. */
    suspend fun log(recipeId: Long, date: LocalDate): Long = dao.insert(CookLog(recipeId = recipeId, cookedOn = date))

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Re-inserts a deleted entry with its original id (undo). */
    suspend fun restore(log: CookLog) {
        dao.insert(log)
    }
}
