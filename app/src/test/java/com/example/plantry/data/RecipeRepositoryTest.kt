package com.example.plantry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeRepositoryTest {

    private val dao = FakeRecipeDao()
    private val repository = RecipeRepository(dao)

    private val sweetPotato = RecipeIngredientDraft("1 große Süßkartoffel", 300.0, ingredientId = 1)
    private val lentils = RecipeIngredientDraft("200 g rote Linsen", 200.0, ingredientId = 2)

    private fun draft(
        bookServings: Int = 4,
        ourServings: Int? = null,
        lines: List<RecipeIngredientDraft> = listOf(sweetPotato, lentils),
    ) = RecipeDraft(
        title = "Linsen-Dal",
        source = "Kochbuch",
        page = 42,
        bookServings = bookServings,
        ourServings = ourServings,
        cookingTimeMinutes = 30,
        lines = lines,
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
                modified = false,
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
    fun create_storesLinesInOrderAndIsNotModified() = runTest {
        val id = repository.create(draft(lines = listOf(sweetPotato.copy(originalText = " 1 Süßkartoffel "), lentils)))

        assertEquals(
            listOf(sweetPotato.copy(originalText = "1 Süßkartoffel"), lentils),
            repository.getLines(id).map { it.toDraft() },
        )
        assertEquals(listOf(0, 1), repository.getLines(id).map { it.position })
        assertFalse(repository.getRecipe(id)!!.modified)
    }

    @Test
    fun update_withSameLines_doesNotFlagModified() = runTest {
        val id = repository.create(draft())

        repository.update(id, draft().copy(title = "Dal", ourServings = 2))

        assertFalse(repository.getRecipe(id)!!.modified)
    }

    @Test
    fun update_withEditedLine_replacesLinesAndFlagsModified() = runTest {
        val id = repository.create(draft())

        repository.update(id, draft(lines = listOf(sweetPotato, lentils.copy(grams = 300.0))))

        assertEquals(listOf(300.0, 300.0), repository.getLines(id).map { it.grams })
        assertTrue(repository.getRecipe(id)!!.modified)
    }

    @Test
    fun update_withAddedOrDeletedLine_flagsModified() = runTest {
        val added = repository.create(draft())
        val deleted = repository.create(draft())

        repository.update(added, draft(lines = listOf(sweetPotato, lentils, sweetPotato)))
        repository.update(deleted, draft(lines = listOf(lentils)))

        assertTrue(repository.getRecipe(added)!!.modified)
        assertTrue(repository.getRecipe(deleted)!!.modified)
        assertEquals(listOf(lentils), repository.getLines(deleted).map { it.toDraft() })
    }

    @Test
    fun update_staysModifiedOnceFlagged() = runTest {
        val id = repository.create(draft())
        repository.update(id, draft(lines = listOf(lentils)))

        repository.update(id, draft(lines = listOf(lentils)).copy(title = "Dal"))

        assertTrue(repository.getRecipe(id)!!.modified)
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

/** In-memory [RecipeDao] mirroring Room's id generation, ordering and cascading line deletes. */
private class FakeRecipeDao : RecipeDao {
    private val recipes = MutableStateFlow<Map<Long, Recipe>>(emptyMap())
    private val lines = MutableStateFlow<List<RecipeIngredient>>(emptyList())
    private var nextId = 1L
    private var nextLineId = 1L

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
        deleteLines(id)
    }

    override fun observeLines(recipeId: Long): Flow<List<RecipeIngredient>> =
        lines.map { all -> all.filter { it.recipeId == recipeId }.sortedBy { it.position } }

    override suspend fun getLines(recipeId: Long): List<RecipeIngredient> = observeLines(recipeId).first()

    override suspend fun getAllLines(): List<RecipeIngredient> = lines.value

    override suspend fun insertLines(lines: List<RecipeIngredient>) {
        this.lines.value += lines.map { it.copy(id = nextLineId++) }
    }

    override suspend fun deleteLines(recipeId: Long) {
        lines.value = lines.value.filter { it.recipeId != recipeId }
    }
}
