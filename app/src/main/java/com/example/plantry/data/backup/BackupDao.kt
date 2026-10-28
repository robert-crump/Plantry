package com.example.plantry.data.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.plantry.data.CookLog
import com.example.plantry.data.CookLogPlant
import com.example.plantry.data.Ingredient
import com.example.plantry.data.IngredientAlias
import com.example.plantry.data.IngredientUnit
import com.example.plantry.data.MixComponent
import com.example.plantry.data.PlannedRecipe
import com.example.plantry.data.Recipe
import com.example.plantry.data.RecipeIngredient

/** Reads and replaces the whole database; backed by [BackupDao] in the app and by a fake in tests. */
interface BackupStore {
    suspend fun snapshot(): BackupSnapshot
    suspend fun replaceAll(snapshot: BackupSnapshot)
}

/**
 * Ordered by key, so the same data always exports to the same file. The proposal event log is
 * left out on purpose: it never leaves the device, and a restore keeps it as it is.
 */
@Dao
abstract class BackupDao : BackupStore {

    @Query("SELECT * FROM ingredients ORDER BY id")
    abstract suspend fun getIngredients(): List<Ingredient>

    @Query("SELECT * FROM ingredient_units ORDER BY ingredientId, unit")
    abstract suspend fun getUnits(): List<IngredientUnit>

    @Query("SELECT * FROM mix_components ORDER BY mixId, componentId")
    abstract suspend fun getComponents(): List<MixComponent>

    @Query("SELECT * FROM recipes ORDER BY id")
    abstract suspend fun getRecipes(): List<Recipe>

    @Query("SELECT * FROM recipe_ingredients ORDER BY recipeId, position")
    abstract suspend fun getLines(): List<RecipeIngredient>

    @Query("SELECT * FROM cook_log ORDER BY id")
    abstract suspend fun getCookLog(): List<CookLog>

    @Query("SELECT * FROM cook_log_plants ORDER BY cookLogId, id")
    abstract suspend fun getCookLogPlants(): List<CookLogPlant>

    @Query("SELECT * FROM ingredient_aliases ORDER BY wording")
    abstract suspend fun getAliases(): List<IngredientAlias>

    @Query("SELECT * FROM planned_recipes ORDER BY recipeId")
    abstract suspend fun getPlanned(): List<PlannedRecipe>

    @Transaction
    override suspend fun snapshot() = BackupSnapshot(
        ingredients = getIngredients(),
        recipes = getRecipes(),
        lines = getLines(),
        cookLog = getCookLog(),
        aliases = getAliases(),
        planned = getPlanned(),
        units = getUnits(),
        components = getComponents(),
        // Their row ids aren't part of the backup.
        cookLogPlants = getCookLogPlants().map { it.copy(id = 0) },
    )

    /** All or nothing: a file with broken references (e.g. an alias pointing nowhere) changes nothing. */
    @Transaction
    override suspend fun replaceAll(snapshot: BackupSnapshot) {
        deleteAliases()
        deletePlanned()
        deleteCookLogPlants()
        deleteCookLog()
        deleteLines()
        deleteRecipes()
        deleteUnits()
        deleteComponents()
        deleteIngredients()
        insertIngredients(snapshot.ingredients)
        insertUnits(snapshot.units)
        insertComponents(snapshot.components)
        insertAliases(snapshot.aliases)
        insertRecipes(snapshot.recipes)
        insertLines(snapshot.lines)
        insertCookLog(snapshot.cookLog)
        insertCookLogPlants(snapshot.cookLogPlants)
        insertPlanned(snapshot.planned)
    }

    @Query("DELETE FROM ingredient_aliases")
    protected abstract suspend fun deleteAliases()

    @Query("DELETE FROM planned_recipes")
    protected abstract suspend fun deletePlanned()

    @Query("DELETE FROM cook_log")
    protected abstract suspend fun deleteCookLog()

    @Query("DELETE FROM cook_log_plants")
    protected abstract suspend fun deleteCookLogPlants()

    @Query("DELETE FROM recipe_ingredients")
    protected abstract suspend fun deleteLines()

    @Query("DELETE FROM recipes")
    protected abstract suspend fun deleteRecipes()

    @Query("DELETE FROM ingredient_units")
    protected abstract suspend fun deleteUnits()

    @Query("DELETE FROM mix_components")
    protected abstract suspend fun deleteComponents()

    @Query("DELETE FROM ingredients")
    protected abstract suspend fun deleteIngredients()

    @Insert
    protected abstract suspend fun insertIngredients(ingredients: List<Ingredient>)

    @Insert
    protected abstract suspend fun insertUnits(units: List<IngredientUnit>)

    @Insert
    protected abstract suspend fun insertComponents(components: List<MixComponent>)

    @Insert
    protected abstract suspend fun insertAliases(aliases: List<IngredientAlias>)

    @Insert
    protected abstract suspend fun insertRecipes(recipes: List<Recipe>)

    @Insert
    protected abstract suspend fun insertLines(lines: List<RecipeIngredient>)

    @Insert
    protected abstract suspend fun insertCookLog(entries: List<CookLog>)

    @Insert
    protected abstract suspend fun insertCookLogPlants(plants: List<CookLogPlant>)

    @Insert
    protected abstract suspend fun insertPlanned(entries: List<PlannedRecipe>)
}
