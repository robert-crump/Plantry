package com.example.plantry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** A recipe logged as cooked, with what to undo: the log entry and the Geplant entry it cleared. */
data class Cooked(val logId: Long, val unplanned: PlannedRecipe?)

/** Geplant: recipes meant to be cooked, until they are logged as cooked or drop off after a week. */
class PlannedRepository(
    private val dao: PlannedRecipeDao,
    private val cookLog: CookLogRepository,
    private val today: () -> LocalDate = LocalDate::now,
) {
    /** Oldest plan first; [today] is read on every emission, so old entries drop off across midnight. */
    fun observe(): Flow<List<PlannedRecipe>> =
        dao.observeAll().map { all -> all.filter { it.isCurrent(today()) } }

    fun observeIsPlanned(recipeId: Long): Flow<Boolean> =
        observe().map { all -> all.any { it.recipeId == recipeId } }

    suspend fun plan(recipeId: Long) = dao.upsert(PlannedRecipe(recipeId, today()))

    /** Returns the removed entry, for undo; null if the recipe wasn't on Geplant. */
    suspend fun remove(recipeId: Long): PlannedRecipe? =
        dao.get(recipeId)?.takeIf { it.isCurrent(today()) }.also { dao.delete(recipeId) }

    /** Puts a removed entry back with its original date (undo). */
    suspend fun restore(planned: PlannedRecipe) = dao.upsert(planned)

    /**
     * Logs [recipeId] as cooked on [date] and takes it off Geplant. Null if the recipe doesn't
     * exist; then Geplant is left alone.
     */
    suspend fun cook(recipeId: Long, date: LocalDate): Cooked? {
        val logId = cookLog.log(recipeId, date) ?: return null
        return Cooked(logId, remove(recipeId))
    }

    /** Undoes [cook]: deletes the log entry and puts the recipe back on Geplant if it was there. */
    suspend fun undoCook(cooked: Cooked) {
        cookLog.delete(cooked.logId)
        cooked.unplanned?.let { restore(it) }
    }
}

/** A Geplant entry with its recipe as it is now. */
data class PlannedItem(val planned: PlannedRecipe, val snapshot: RecipeSnapshot) {
    val recipeId: Long get() = planned.recipeId

    companion object {
        /** In the order of [planned]; entries whose recipe is missing are skipped. */
        fun of(
            planned: List<PlannedRecipe>,
            recipes: List<Recipe>,
            lines: List<RecipeIngredient>,
            ingredients: Map<Long, Ingredient>,
        ): List<PlannedItem> {
            val recipesById = recipes.associateBy { it.id }
            val linesByRecipe = lines.groupBy { it.recipeId }
            return planned.mapNotNull { entry ->
                val recipe = recipesById[entry.recipeId] ?: return@mapNotNull null
                PlannedItem(entry, RecipeSnapshot.of(recipe, linesByRecipe[recipe.id].orEmpty(), ingredients))
            }
        }
    }
}
