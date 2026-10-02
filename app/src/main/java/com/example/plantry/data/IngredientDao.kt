package com.example.plantry.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {

    @Query("SELECT * FROM ingredients ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Ingredient>>

    @Query("SELECT * FROM ingredients WHERE id = :id")
    fun observeById(id: Long): Flow<Ingredient?>

    @Query("SELECT * FROM ingredients WHERE id = :id")
    suspend fun getById(id: Long): Ingredient?

    @Query("SELECT id, buyAsIngredientId FROM ingredients")
    suspend fun getBuyAsLinks(): List<BuyAsLink>

    @Insert
    suspend fun insert(ingredient: Ingredient): Long

    @Update
    suspend fun update(ingredient: Ingredient)
}
