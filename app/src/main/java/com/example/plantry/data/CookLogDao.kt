package com.example.plantry.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface CookLogDao {

    @Insert
    suspend fun insert(log: CookLog): Long

    @Insert
    suspend fun insertPlants(plants: List<CookLogPlant>)

    /** Stores [log] with [plants] (their [CookLogPlant.cookLogId] is ignored) and returns its id. */
    @Transaction
    suspend fun insert(log: CookLog, plants: List<CookLogPlant>): Long {
        val id = insert(log)
        insertPlants(plants.map { it.copy(id = 0, cookLogId = id) })
        return id
    }

    /** Also deletes its plants. */
    @Query("DELETE FROM cook_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM cook_log_plants ORDER BY cookLogId, id")
    fun observePlants(): Flow<List<CookLogPlant>>

    @Query("SELECT * FROM cook_log_plants ORDER BY cookLogId, id")
    suspend fun getPlants(): List<CookLogPlant>

    /** Newest first; entries of the same day in the order they were logged, newest first. */
    @Query("SELECT * FROM cook_log ORDER BY cookedOn DESC, id DESC")
    fun observeHistory(): Flow<List<CookLog>>

    @Query("SELECT cookedOn FROM cook_log WHERE recipeId = :recipeId")
    fun observeDates(recipeId: Long): Flow<List<LocalDate>>

    /** The latest cook date of every existing recipe that was cooked at least once. */
    @Query("SELECT recipeId, MAX(cookedOn) AS lastCookedOn FROM cook_log WHERE recipeId IS NOT NULL GROUP BY recipeId")
    suspend fun getLastCooked(): List<LastCooked>

    @Query("SELECT recipeId, MAX(cookedOn) AS lastCookedOn FROM cook_log WHERE recipeId IS NOT NULL GROUP BY recipeId")
    fun observeLastCooked(): Flow<List<LastCooked>>
}
