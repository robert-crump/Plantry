package com.example.plantry.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface WeekPlanDao {

    @Query(
        """
        SELECT week_plan_slots.*, recipes.title AS recipeTitle
        FROM week_plan_slots INNER JOIN recipes ON recipes.id = week_plan_slots.recipeId
        WHERE week_plan_slots.weekStart = :weekStart
        ORDER BY week_plan_slots.position
        """,
    )
    fun observeWeek(weekStart: LocalDate): Flow<List<PlannedRecipe>>

    @Query("SELECT * FROM week_plan_slots WHERE weekStart = :weekStart ORDER BY position")
    fun observeSlots(weekStart: LocalDate): Flow<List<WeekPlanSlot>>

    @Query("SELECT * FROM week_plan_slots WHERE weekStart = :weekStart ORDER BY position")
    suspend fun getSlots(weekStart: LocalDate): List<WeekPlanSlot>

    @Upsert
    suspend fun upsert(slot: WeekPlanSlot)

    @Insert
    suspend fun insertAll(slots: List<WeekPlanSlot>)

    @Query("DELETE FROM week_plan_slots WHERE weekStart = :weekStart AND position = :position")
    suspend fun delete(weekStart: LocalDate, position: Int)

    @Query("UPDATE week_plan_slots SET done = :done WHERE weekStart = :weekStart AND position = :position")
    suspend fun setDone(weekStart: LocalDate, position: Int, done: Boolean)
}
