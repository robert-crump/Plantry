package com.example.plantry.data.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.plantry.data.CookLog
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientAlias
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeIngredient
import com.example.plantry.data.ShoppingTick
import com.example.plantry.data.WeekPlanSlot

/** Reads and replaces the whole database; backed by [BackupDao] in the app and by a fake in tests. */
interface BackupStore {
    suspend fun snapshot(): BackupSnapshot
    suspend fun replaceAll(snapshot: BackupSnapshot)
}

/** Ordered by key, so the same data always exports to the same file. */
@Dao
abstract class BackupDao : BackupStore {

    @Query("SELECT * FROM ingredients ORDER BY id")
    abstract suspend fun getIngredients(): List<Ingredient>

    @Query("SELECT * FROM recipes ORDER BY id")
    abstract suspend fun getRecipes(): List<Recipe>

    @Query("SELECT * FROM recipe_ingredients ORDER BY recipeId, position")
    abstract suspend fun getLines(): List<RecipeIngredient>

    @Query("SELECT * FROM cook_log ORDER BY id")
    abstract suspend fun getCookLog(): List<CookLog>

    @Query("SELECT * FROM week_plan_slots ORDER BY weekStart, position")
    abstract suspend fun getWeekPlan(): List<WeekPlanSlot>

    @Query("SELECT * FROM shopping_ticks ORDER BY weekStart, ingredientId")
    abstract suspend fun getShoppingTicks(): List<ShoppingTick>

    @Query("SELECT * FROM ingredient_aliases ORDER BY wording")
    abstract suspend fun getAliases(): List<IngredientAlias>

    @Transaction
    override suspend fun snapshot() = BackupSnapshot(
        ingredients = getIngredients(),
        recipes = getRecipes(),
        lines = getLines(),
        cookLog = getCookLog(),
        weekPlan = getWeekPlan(),
        shoppingTicks = getShoppingTicks(),
        aliases = getAliases(),
    )

    /** All or nothing: a file with broken references (e.g. a line pointing nowhere) changes nothing. */
    @Transaction
    override suspend fun replaceAll(snapshot: BackupSnapshot) {
        deleteAliases()
        deleteShoppingTicks()
        deleteWeekPlan()
        deleteCookLog()
        deleteLines()
        deleteRecipes()
        deleteIngredients()
        // Buy-as links may point to ingredients inserted later, so they are set in a second pass.
        insertIngredients(snapshot.ingredients.map { it.copy(buyAsIngredientId = null) })
        snapshot.ingredients.forEach { ingredient ->
            ingredient.buyAsIngredientId?.let { setBuyAs(ingredient.id, it) }
        }
        insertAliases(snapshot.aliases)
        insertRecipes(snapshot.recipes)
        insertLines(snapshot.lines)
        insertCookLog(snapshot.cookLog)
        insertWeekPlan(snapshot.weekPlan)
        insertShoppingTicks(snapshot.shoppingTicks)
    }

    @Query("DELETE FROM ingredient_aliases")
    protected abstract suspend fun deleteAliases()

    @Query("DELETE FROM shopping_ticks")
    protected abstract suspend fun deleteShoppingTicks()

    @Query("DELETE FROM week_plan_slots")
    protected abstract suspend fun deleteWeekPlan()

    @Query("DELETE FROM cook_log")
    protected abstract suspend fun deleteCookLog()

    @Query("DELETE FROM recipe_ingredients")
    protected abstract suspend fun deleteLines()

    @Query("DELETE FROM recipes")
    protected abstract suspend fun deleteRecipes()

    @Query("DELETE FROM ingredients")
    protected abstract suspend fun deleteIngredients()

    @Insert
    protected abstract suspend fun insertIngredients(ingredients: List<Ingredient>)

    @Insert
    protected abstract suspend fun insertAliases(aliases: List<IngredientAlias>)

    @Query("UPDATE ingredients SET buyAsIngredientId = :buyAsIngredientId WHERE id = :id")
    protected abstract suspend fun setBuyAs(id: Long, buyAsIngredientId: Long)

    @Insert
    protected abstract suspend fun insertRecipes(recipes: List<Recipe>)

    @Insert
    protected abstract suspend fun insertLines(lines: List<RecipeIngredient>)

    @Insert
    protected abstract suspend fun insertCookLog(entries: List<CookLog>)

    @Insert
    protected abstract suspend fun insertWeekPlan(slots: List<WeekPlanSlot>)

    @Insert
    protected abstract suspend fun insertShoppingTicks(ticks: List<ShoppingTick>)
}
