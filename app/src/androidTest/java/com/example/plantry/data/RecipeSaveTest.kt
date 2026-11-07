package com.example.plantry.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.plantry.data.backup.PhotoStore
import com.example.plantry.data.backup.Photos
import com.example.plantry.data.claude.IngredientProposal
import com.example.plantry.data.claude.NutritionSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** [RecipeRepository.save] and [RecipeRepository.delete] against a real database, so the transaction rolls back. */
@RunWith(AndroidJUnit4::class)
class RecipeSaveTest {

    private lateinit var db: PlantryDatabase
    private lateinit var ingredients: IngredientRepository
    private val store = MemoryPhotoStore()
    private val bookSession = BookSession()
    private lateinit var repository: RecipeRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PlantryDatabase::class.java,
        ).build()
        ingredients = IngredientRepository(db.ingredientDao())
        repository = RecipeRepository(db.recipeDao(), ingredients, RecipePhotoRepository(store), bookSession) { db.withTransaction(it) }
    }

    @After
    fun tearDown() = db.close()

    private fun proposal(name: String) = IngredientProposal(
        name = name,
        source = NutritionSource.Label(Nutrition()),
        searchTerms = emptyList(),
        storeSection = StoreSection.PRODUCE,
        plantPoints = PlantPoints.ONE,
    )

    private fun line(ingredientId: Long, wording: String? = null) =
        RecipeIngredientDraft(100.0, LineUnit.Grams, ingredientId, wording)

    private fun draft(vararg lines: RecipeIngredientDraft) =
        RecipeDraft(title = "Linsen-Dal", source = "Plenty", page = 113, servings = 4, totalTimeMinutes = 30, lines = lines.toList())

    private suspend fun existing(name: String): Long = ingredients.createProposed(mapOf(-99L to proposal(name))).getValue(-99L)

    private suspend fun ingredientNames() = db.ingredientDao().observeAll().first().map { it.name }.sorted()

    private suspend fun aliases() = ingredients.observeAliases().first()

    @Test
    fun save_newScan_createsProposedIngredientsAndPointsLinesAndAliasesAtThem() = runTest {
        val lentils = existing("Linsen")

        val id = repository.save(
            RecipeSave(
                recipeId = null,
                draft = draft(line(lentils, "200 g rote Linsen"), line(-1, "1 Süßkartoffel")),
                newIngredients = mapOf(-1L to proposal("Süßkartoffel")),
                aliases = listOf("rote Linsen" to lentils, "Süßkartoffel" to -1L),
                addPhoto = byteArrayOf(1),
                bookPage = BookPage("Plenty", 113),
            ),
        )

        val sweetPotato = db.ingredientDao().observeAll().first().single { it.name == "Süßkartoffel" }.id
        assertEquals("Linsen-Dal", repository.getRecipe(id)?.title)
        assertEquals(listOf(lentils, sweetPotato), repository.getLines(id).map { it.ingredientId })
        assertEquals(sweetPotato, aliases()["süßkartoffel"])
        assertEquals(lentils, aliases()["rote linsen"])
        assertEquals(1, store.list(id).size)
        assertEquals(BookPage("Plenty", 114), bookSession.defaults(BookPage("", null)))
    }

    @Test
    fun save_failingLine_rollsBackIngredientsAliasesAndRecipe() = runTest {
        val before = ingredientNames()

        try {
            repository.save(
                RecipeSave(
                    recipeId = null,
                    // 9999 is no ingredient: inserting the lines breaks the foreign key, after the proposal was created.
                    draft = draft(line(-1), line(9999)),
                    newIngredients = mapOf(-1L to proposal("Süßkartoffel")),
                    aliases = listOf("Süßkartoffel" to -1L),
                    addPhoto = byteArrayOf(1),
                    bookPage = BookPage("Plenty", 113),
                ),
            )
            fail("save should throw")
        } catch (expected: Exception) {
            // The constraint failure.
        }

        assertEquals(before, ingredientNames())
        assertTrue(aliases().isEmpty())
        assertTrue(repository.observeRecipes().first().isEmpty())
        assertTrue(store.pages.isEmpty())
        assertEquals(BookPage("", null), bookSession.defaults(BookPage("", null)))
    }

    @Test
    fun save_update_addsAndDeletesPages() = runTest {
        val lentils = existing("Linsen")
        val id = repository.save(RecipeSave(recipeId = null, draft = draft(line(lentils)), addPhoto = byteArrayOf(1)))
        store.add(id, byteArrayOf(2))

        repository.save(RecipeSave(recipeId = id, draft = draft(line(lentils)), addPhoto = byteArrayOf(3), deletedPages = setOf(0)))

        assertEquals(listOf(2.toByte(), 3.toByte()), store.list(id).map { it.single() })
    }

    @Test
    fun save_photoFailsAfterCommit_recipeStaysSaved() = runTest {
        val lentils = existing("Linsen")
        store.failAdd = true

        val id = repository.save(RecipeSave(recipeId = null, draft = draft(line(lentils)), addPhoto = byteArrayOf(1)))

        assertNotNull(repository.getRecipe(id))
        assertTrue(store.list(id).isEmpty())
    }

    @Test
    fun delete_removesRecipeAndItsPhotos() = runTest {
        val lentils = existing("Linsen")
        val id = repository.save(RecipeSave(recipeId = null, draft = draft(line(lentils)), addPhoto = byteArrayOf(1)))

        repository.delete(id)

        assertEquals(null, repository.getRecipe(id))
        assertTrue(store.list(id).isEmpty())
    }
}

/** Page photos in memory; [failAdd] makes adding throw like a full disk. */
private class MemoryPhotoStore : PhotoStore {
    val pages = mutableMapOf<Long, MutableList<ByteArray>>()
    var failAdd = false

    override fun all(): Photos = pages.mapValues { it.value.toList() }
    override fun replaceAll(photos: Photos) {
        pages.clear()
        photos.forEach { (id, list) -> pages[id] = list.toMutableList() }
    }
    override fun list(recipeId: Long): List<ByteArray> = pages[recipeId].orEmpty()
    override fun add(recipeId: Long, bytes: ByteArray) {
        if (failAdd) throw java.io.IOException("disk full")
        pages.getOrPut(recipeId) { mutableListOf() }.add(bytes)
    }
    override fun delete(recipeId: Long, index: Int) {
        pages[recipeId]?.removeAt(index)
    }
    override fun deleteAll(recipeId: Long) {
        pages.remove(recipeId)
    }
}
