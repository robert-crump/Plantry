package com.example.plantry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class CookLogRepository(
    private val dao: CookLogDao,
    /** The recipe as it is now; null if it doesn't exist. */
    private val snapshot: suspend (recipeId: Long) -> RecipeSnapshot?,
) {
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

    /**
     * Logs [recipeId] as cooked on [date] with a snapshot of the recipe and its plants as it is now and returns
     * the entry id, e.g. for undo; null if the recipe doesn't exist.
     */
    suspend fun log(recipeId: Long, date: LocalDate): Long? {
        val snapshot = snapshot(recipeId) ?: return null
        return dao.insert(CookLog.of(recipeId, date, snapshot), snapshot.plants)
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
