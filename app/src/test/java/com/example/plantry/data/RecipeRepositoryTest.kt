package com.example.plantry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeRepositoryTest {

    private val dao = FakeRecipeDao()
    private val repository = RecipeRepository(dao)

    private fun draft(bookServings: Int = 4, ourServings: Int? = null) = RecipeDraft(
        title = "Linsen-Dal",
        source = "Kochbuch",
        page = 42,
        bookServings = bookServings,
        ourServings = ourServings,
        cookingTimeMinutes = 30,
    )

    @Test
    fun create_withoutOurServings_defaultsToBookServings() = runTest {
        val id = repository.create(draft(bookServings = 4, ourServings = null))

        assertEquals(4, repository.getRecipe(id)!!.ourServings)
    }

    @Test
    fun create_withOurServings_keepsThem() = runTest {
        val id = repository.create(draft(bookServings = 4, ourServings = 2))

        assertEquals(2, repository.getRecipe(id)!!.ourServings)
    }

    @Test
    fun create_storesAllFieldsAndTrimsText() = runTest {
        val id = repository.create(draft().copy(title = "  Linsen-Dal ", source = " Kochbuch  "))

        assertEquals(
            Recipe(
                id = id,
                title = "Linsen-Dal",
                source = "Kochbuch",
                page = 42,
                bookServings = 4,
                ourServings = 4,
                cookingTimeMinutes = 30,
            ),
            repository.getRecipe(id),
        )
    }

    @Test
    fun update_replacesFieldsOfExistingRecipe() = runTest {
        val id = repository.create(draft())

        repository.update(id, draft().copy(title = "Chili", page = null, ourServings = 6))

        val updated = repository.getRecipe(id)!!
        assertEquals("Chili", updated.title)
        assertNull(updated.page)
        assertEquals(6, updated.ourServings)
        assertEquals(1, repository.observeRecipes().first().size)
    }

    @Test
    fun update_withoutOurServings_fallsBackToBookServings() = runTest {
        val id = repository.create(draft(ourServings = 2))

        repository.update(id, draft(bookServings = 3, ourServings = null))

        assertEquals(3, repository.getRecipe(id)!!.ourServings)
    }

    @Test
    fun delete_removesRecipe() = runTest {
        val keep = repository.create(draft().copy(title = "Chili"))
        val remove = repository.create(draft())

        repository.delete(remove)

        assertNull(repository.getRecipe(remove))
        assertEquals(listOf(keep), repository.observeRecipes().first().map { it.id })
    }

    @Test
    fun observeRecipe_emitsCurrentState() = runTest {
        val id = repository.create(draft())

        assertEquals("Linsen-Dal", repository.observeRecipe(id).first()?.title)
        repository.delete(id)
        assertNull(repository.observeRecipe(id).first())
    }
}

/** In-memory [RecipeDao] mirroring Room's id generation and title ordering. */
private class FakeRecipeDao : RecipeDao {
    private val recipes = MutableStateFlow<Map<Long, Recipe>>(emptyMap())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Recipe>> =
        recipes.map { all -> all.values.sortedBy { it.title.lowercase() } }

    override fun observeById(id: Long): Flow<Recipe?> = recipes.map { it[id] }

    override suspend fun getById(id: Long): Recipe? = recipes.value[id]

    override suspend fun insert(recipe: Recipe): Long {
        val id = nextId++
        recipes.value += id to recipe.copy(id = id)
        return id
    }

    override suspend fun update(recipe: Recipe) {
        if (recipe.id in recipes.value) recipes.value += recipe.id to recipe
    }

    override suspend fun deleteById(id: Long) {
        recipes.value -= id
    }
}
