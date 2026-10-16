package com.example.plantry.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Insert
    suspend fun insert(ingredient: Ingredient): Long

    @Query("SELECT * FROM ingredients WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Ingredient>

    @Update
    suspend fun update(ingredient: Ingredient)

    @Update
    suspend fun updateAll(ingredients: List<Ingredient>)

    @Query("UPDATE ingredients SET reviewed = 1 WHERE id IN (:ids) AND storeSectionUndecided = 0 AND plantPointsUndecided = 0")
    suspend fun markReviewed(ids: List<Long>)

    /** Titles of the recipes with a line of [id]; while there are any, the ingredient can't be deleted. */
    @Query(
        """
        SELECT DISTINCT r.title FROM recipes r JOIN recipe_ingredients ri ON ri.recipeId = r.id
        WHERE ri.ingredientId = :id ORDER BY r.title COLLATE NOCASE
        """,
    )
    suspend fun getRecipeTitlesUsing(id: Long): List<String>

    /** Titles of the recipes with a line in [unit] of ingredient [id]; while there are any, the unit can't be removed. */
    @Query(
        """
        SELECT DISTINCT r.title FROM recipes r JOIN recipe_ingredients ri ON ri.recipeId = r.id
        WHERE ri.ingredientId = :id AND ri.unit = :unit ORDER BY r.title COLLATE NOCASE
        """,
    )
    suspend fun getRecipeTitlesUsingUnit(id: Long, unit: PieceUnit): List<String>

    @Query("SELECT * FROM ingredient_units ORDER BY ingredientId, unit")
    fun observeUnits(): Flow<List<IngredientUnit>>

    @Query("SELECT * FROM ingredient_units WHERE ingredientId = :id ORDER BY unit")
    suspend fun getUnits(id: Long): List<IngredientUnit>

    @Insert
    suspend fun insertUnits(units: List<IngredientUnit>)

    @Query("DELETE FROM ingredient_units WHERE ingredientId = :id")
    suspend fun deleteUnits(id: Long)

    /** Inserts [ingredient] with [units] (whose ingredient id is ignored) and returns the new id. */
    @Transaction
    suspend fun insertWithUnits(ingredient: Ingredient, units: List<IngredientUnit>): Long {
        val id = insert(ingredient)
        insertUnits(units.map { it.copy(ingredientId = id) })
        return id
    }

    /** Updates [ingredient] and replaces all of its units with [units]. */
    @Transaction
    suspend fun updateWithUnits(ingredient: Ingredient, units: List<IngredientUnit>) {
        update(ingredient)
        deleteUnits(ingredient.id)
        insertUnits(units.map { it.copy(ingredientId = ingredient.id) })
    }

    /** Aliases and units go with the ingredient. */
    @Query("DELETE FROM ingredients WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM ingredient_aliases")
    fun observeAliases(): Flow<List<IngredientAlias>>

    /** A wording already known points to the new ingredient afterwards. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAliases(aliases: List<IngredientAlias>)
}
