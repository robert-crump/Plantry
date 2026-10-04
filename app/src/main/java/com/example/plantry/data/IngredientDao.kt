package com.example.plantry.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Query("SELECT * FROM ingredients WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Ingredient>

    @Update
    suspend fun update(ingredient: Ingredient)

    @Update
    suspend fun updateAll(ingredients: List<Ingredient>)

    @Query("UPDATE ingredients SET reviewed = 1 WHERE id IN (:ids)")
    suspend fun markReviewed(ids: List<Long>)

    /** Titles of the recipes with a line of [id]; while there are any, the ingredient can't be deleted. */
    @Query(
        """
        SELECT DISTINCT r.title FROM recipes r JOIN recipe_ingredients ri ON ri.recipeId = r.id
        WHERE ri.ingredientId = :id ORDER BY r.title COLLATE NOCASE
        """,
    )
    suspend fun getRecipeTitlesUsing(id: Long): List<String>

    /** Aliases go with the ingredient; ingredients bought as it lose their buy-as link. */
    @Query("DELETE FROM ingredients WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM ingredient_aliases")
    fun observeAliases(): Flow<List<IngredientAlias>>

    /** A wording already known points to the new ingredient afterwards. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAliases(aliases: List<IngredientAlias>)
}
