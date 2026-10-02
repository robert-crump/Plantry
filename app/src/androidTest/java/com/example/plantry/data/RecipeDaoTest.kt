package com.example.plantry.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeDaoTest {

    private lateinit var db: PlantryDatabase
    private lateinit var dao: RecipeDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PlantryDatabase::class.java,
        ).build()
        dao = db.recipeDao()
    }

    @After
    fun tearDown() = db.close()

    private fun recipe(title: String, page: Int? = 12) = Recipe(
        title = title,
        source = "Kochbuch",
        page = page,
        bookServings = 4,
        ourServings = 2,
        cookingTimeMinutes = 30,
    )

    @Test
    fun insert_thenGetById_returnsRecipeWithGeneratedId() = runTest {
        val id = dao.insert(recipe("Chili", page = null))

        assertEquals(recipe("Chili", page = null).copy(id = id), dao.getById(id))
    }

    @Test
    fun observeAll_ordersByTitleIgnoringCase() = runTest {
        dao.insert(recipe("linsen-Dal"))
        dao.insert(recipe("Chili"))
        dao.insert(recipe("Bowl"))

        assertEquals(listOf("Bowl", "Chili", "linsen-Dal"), dao.observeAll().first().map { it.title })
    }

    @Test
    fun update_changesStoredRecipe() = runTest {
        val id = dao.insert(recipe("Chili"))

        dao.update(recipe("Chili sin Carne").copy(id = id, ourServings = 6))

        val updated = dao.observeById(id).first()!!
        assertEquals("Chili sin Carne", updated.title)
        assertEquals(6, updated.ourServings)
    }

    @Test
    fun deleteById_removesOnlyThatRecipe() = runTest {
        val keep = dao.insert(recipe("Bowl"))
        val remove = dao.insert(recipe("Chili"))

        dao.deleteById(remove)

        assertNull(dao.getById(remove))
        assertEquals(listOf(keep), dao.observeAll().first().map { it.id })
    }
}
