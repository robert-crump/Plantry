package com.example.plantry.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ShoppingTickDao {

    @Query("SELECT ingredientId FROM shopping_ticks WHERE weekStart = :weekStart")
    fun observeTicked(weekStart: LocalDate): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tick: ShoppingTick)

    @Query("DELETE FROM shopping_ticks WHERE weekStart = :weekStart AND ingredientId = :ingredientId")
    suspend fun delete(weekStart: LocalDate, ingredientId: Long)
}
