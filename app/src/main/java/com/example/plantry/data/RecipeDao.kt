package com.example.plantry.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeById(id: Long): Flow<Recipe?>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: Long): Recipe?

    @Insert
    suspend fun insert(recipe: Recipe): Long

    @Update
    suspend fun update(recipe: Recipe)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId ORDER BY position")
    fun observeLines(recipeId: Long): Flow<List<RecipeIngredient>>

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId ORDER BY position")
    suspend fun getLines(recipeId: Long): List<RecipeIngredient>

    @Query("SELECT * FROM recipe_ingredients")
    suspend fun getAllLines(): List<RecipeIngredient>

    @Query("SELECT * FROM recipe_ingredients")
    fun observeAllLines(): Flow<List<RecipeIngredient>>

    @Insert
    suspend fun insertLines(lines: List<RecipeIngredient>)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun deleteLines(recipeId: Long)

    /** Inserts [recipe] with [lines] and returns the new recipe id. */
    @Transaction
    suspend fun insertWithLines(recipe: Recipe, lines: List<RecipeIngredientDraft>): Long {
        val id = insert(recipe)
        insertLines(lines.toEntities(id))
        return id
    }

    /** Updates [recipe] and replaces all of its lines with [lines]. */
    @Transaction
    suspend fun updateWithLines(recipe: Recipe, lines: List<RecipeIngredientDraft>) {
        update(recipe)
        deleteLines(recipe.id)
        insertLines(lines.toEntities(recipe.id))
    }
}
