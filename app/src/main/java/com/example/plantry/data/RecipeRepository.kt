package com.example.plantry.data

import kotlinx.coroutines.flow.Flow

class RecipeRepository(private val dao: RecipeDao) {

    fun observeRecipes(): Flow<List<Recipe>> = dao.observeAll()

    fun observeRecipe(id: Long): Flow<Recipe?> = dao.observeById(id)

    suspend fun getRecipe(id: Long): Recipe? = dao.getById(id)

    /** Inserts a new recipe and returns its id. */
    suspend fun create(draft: RecipeDraft): Long = dao.insert(draft.toRecipe(id = 0))

    suspend fun update(id: Long, draft: RecipeDraft) = dao.update(draft.toRecipe(id))

    suspend fun delete(id: Long) = dao.deleteById(id)

    private fun RecipeDraft.toRecipe(id: Long) = Recipe(
        id = id,
        title = title.trim(),
        source = source.trim(),
        page = page,
        bookServings = bookServings,
        ourServings = ourServings ?: bookServings,
        cookingTimeMinutes = cookingTimeMinutes,
    )
}
