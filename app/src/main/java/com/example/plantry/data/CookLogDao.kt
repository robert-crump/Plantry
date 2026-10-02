package com.example.plantry.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface CookLogDao {

    @Insert
    suspend fun insert(log: CookLog): Long

    @Query("DELETE FROM cook_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Newest first; entries of the same day in the order they were logged, newest first. */
    @Query(
        """
        SELECT cook_log.*, recipes.title AS recipeTitle
        FROM cook_log INNER JOIN recipes ON recipes.id = cook_log.recipeId
        ORDER BY cook_log.cookedOn DESC, cook_log.id DESC
        """,
    )
    fun observeHistory(): Flow<List<CookLogEntry>>

    @Query("SELECT cookedOn FROM cook_log WHERE recipeId = :recipeId")
    fun observeDates(recipeId: Long): Flow<List<LocalDate>>
}
