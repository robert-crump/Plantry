package com.example.plantry.data

import kotlinx.coroutines.flow.Flow

class RecipeRepository(private val dao: RecipeDao) {

    fun observeRecipes(): Flow<List<Recipe>> = dao.observeAll()

    fun observeRecipe(id: Long): Flow<Recipe?> = dao.observeById(id)

    fun observeLines(recipeId: Long): Flow<List<RecipeLine>> = dao.observeLines(recipeId)

    /** The lines of all recipes. */
    fun observeAllLines(): Flow<List<RecipeLine>> = dao.observeAllLines()

    suspend fun getRecipe(id: Long): Recipe? = dao.getById(id)

    suspend fun getLines(recipeId: Long): List<RecipeLine> = dao.getLines(recipeId)

    /** Inserts a new recipe with its lines and returns its id. */
    suspend fun create(draft: RecipeDraft): Long =
        dao.insertWithLines(draft.toRecipe(id = 0, modified = false), draft.lines)

    /**
     * Overwrites the recipe and its lines. Once its lines differ from the stored ones, the recipe
     * is flagged as modified, and stays so. The [Recipe.notes] are kept, the editor has no field for them.
     */
    suspend fun update(id: Long, draft: RecipeDraft) {
        val existing = dao.getById(id) ?: return
        val linesChanged = dao.getLines(id).map { it.toDraft() } != draft.normalizedLines()
        dao.updateWithLines(
            draft.toRecipe(id, modified = existing.modified || linesChanged).copy(notes = existing.notes),
            draft.lines,
        )
    }

    /** Replaces the recipe's [Recipe.notes], trimmed; blank text clears them. */
    suspend fun setNotes(id: Long, notes: String) = dao.updateNotes(id, notes.trim())

    suspend fun delete(id: Long) = dao.deleteById(id)

    private fun RecipeDraft.normalizedLines() = lines.map { it.normalized() }

    private fun RecipeDraft.toRecipe(id: Long, modified: Boolean) = Recipe(
        id = id,
        title = title.trim(),
        source = source.trim(),
        page = page,
        servings = servings,
        totalTimeMinutes = totalTimeMinutes,
        modified = modified,
        dish = dish,
        activeTimeMinutes = activeTimeMinutes,
        steps = steps,
    )
}
