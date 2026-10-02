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

    private suspend fun insertIngredient(name: String) = db.ingredientDao().insert(
        Ingredient(
            name = name,
            fdcId = null,
            usdaDescription = null,
            nutrition = Nutrition(protein = 10.0),
            unitWeights = emptyList(),
            buyUnit = BuyUnit.GRAMS,
            packSizeGrams = null,
            storeSection = StoreSection.OTHER,
            staple = false,
            plantPoints = PlantPoints.ZERO,
            buyAsIngredientId = null,
            buyAsYieldFactor = null,
            reviewed = true,
        ),
    )

    @Test
    fun insertWithLines_storesLinesInOrder() = runTest {
        val tofu = insertIngredient("Tofu")
        val rice = insertIngredient("Reis")
        val lines = listOf(RecipeIngredientDraft("1 Tasse Reis", 185.0, rice), RecipeIngredientDraft("Tofu", 200.0, tofu))

        val id = dao.insertWithLines(recipe("Bowl"), lines)

        assertEquals(lines, dao.observeLines(id).first().map { it.toDraft() })
        assertEquals(listOf(0, 1), dao.getLines(id).map { it.position })
    }

    @Test
    fun updateWithLines_replacesAllLines() = runTest {
        val tofu = insertIngredient("Tofu")
        val id = dao.insertWithLines(recipe("Bowl"), listOf(RecipeIngredientDraft("Tofu", 200.0, tofu)))

        dao.updateWithLines(
            recipe("Bowl").copy(id = id, modified = true),
            listOf(RecipeIngredientDraft("Tofu", 300.0, tofu), RecipeIngredientDraft("mehr Tofu", 50.0, tofu)),
        )

        assertEquals(listOf(300.0, 50.0), dao.getLines(id).map { it.grams })
        assertEquals(true, dao.getById(id)!!.modified)
    }

    @Test
    fun deleteById_cascadesToLines() = runTest {
        val tofu = insertIngredient("Tofu")
        val id = dao.insertWithLines(recipe("Bowl"), listOf(RecipeIngredientDraft("Tofu", 200.0, tofu)))

        dao.deleteById(id)

        assertEquals(emptyList<RecipeIngredient>(), dao.getLines(id))
    }
}
