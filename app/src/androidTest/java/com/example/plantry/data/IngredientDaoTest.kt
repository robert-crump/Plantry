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
class IngredientDaoTest {

    private lateinit var db: PlantryDatabase
    private lateinit var dao: IngredientDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PlantryDatabase::class.java,
        ).build()
        dao = db.ingredientDao()
    }

    @After
    fun tearDown() = db.close()

    private fun ingredient(name: String) = Ingredient(
        name = name,
        fdcId = 168877,
        usdaDescription = "Rice, white, long-grain, raw",
        nutrition = Nutrition(365.0, 7.13, 79.95, 0.12, 0.66, 1.3),
        storeSection = StoreSection.DRY_GOODS,
        plantPoints = PlantPoints.ZERO,
        reviewed = true,
    )

    private fun recipe(title: String) = Recipe(
        title = title,
        source = "Buch",
        page = null,
        bookServings = 2,
        ourServings = 2,
        cookingTimeMinutes = null,
    )

    private fun line(ingredientId: Long) = RecipeIngredientDraft("100 g", 100.0, ingredientId)

    @Test
    fun insert_thenGetById_roundTripsAllAttributes() = runTest {
        val id = dao.insert(ingredient("Reis, trocken"))

        assertEquals(ingredient("Reis, trocken").copy(id = id), dao.getById(id))
    }

    @Test
    fun observeAll_ordersByNameIgnoringCase() = runTest {
        dao.insert(ingredient("Zwiebel"))
        dao.insert(ingredient("apfel"))
        dao.insert(ingredient("Birne"))

        assertEquals(listOf("apfel", "Birne", "Zwiebel"), dao.observeAll().first().map { it.name })
    }

    @Test
    fun upsertAliases_replacesAWordingAndCascadesWithTheIngredient() = runTest {
        val dry = dao.insert(ingredient("Reis, trocken"))
        val cooked = dao.insert(ingredient("Reis, gekocht"))
        dao.upsertAliases(listOf(IngredientAlias("reis", dry), IngredientAlias("basmati", dry)))
        dao.upsertAliases(listOf(IngredientAlias("reis", cooked)))

        assertEquals(
            setOf(IngredientAlias("reis", cooked), IngredientAlias("basmati", dry)),
            dao.observeAliases().first().toSet(),
        )

        db.openHelper.writableDatabase.execSQL("DELETE FROM ingredients WHERE id = $dry")

        assertEquals(listOf(IngredientAlias("reis", cooked)), dao.observeAliases().first())
    }

    @Test
    fun getRecipeTitlesUsing_listsEachRecipeOnceByTitle() = runTest {
        val rice = dao.insert(ingredient("Reis, trocken"))
        val other = dao.insert(ingredient("Basmati"))
        val recipes = db.recipeDao()
        recipes.insertWithLines(recipe("Risotto"), listOf(line(rice), line(rice)))
        recipes.insertWithLines(recipe("curry"), listOf(line(rice)))
        recipes.insertWithLines(recipe("Pilaw"), listOf(line(other)))

        assertEquals(listOf("curry", "Risotto"), dao.getRecipeTitlesUsing(rice))
    }

    @Test
    fun deleteById_removesAliases() = runTest {
        val dry = dao.insert(ingredient("Reis, trocken"))
        val cooked = dao.insert(ingredient("Reis, gekocht"))
        dao.upsertAliases(listOf(IngredientAlias("reis", dry)))

        dao.deleteById(dry)

        assertNull(dao.getById(dry))
        assertEquals("Reis, gekocht", dao.getById(cooked)?.name)
        assertEquals(emptyList<IngredientAlias>(), dao.observeAliases().first())
    }

    @Test
    fun bulkUpdate_getByIdsUpdateAllAndMarkReviewed() = runTest {
        val dry = dao.insert(ingredient("Reis, trocken").copy(reviewed = false))
        val cooked = dao.insert(ingredient("Reis, gekocht").copy(reviewed = false))
        val other = dao.insert(ingredient("Basmati").copy(reviewed = false))

        val selected = dao.getByIds(listOf(dry, cooked))
        assertEquals(setOf(dry, cooked), selected.map { it.id }.toSet())
        dao.updateAll(selected.map { it.copy(plantPoints = PlantPoints.ONE) })
        dao.markReviewed(listOf(cooked, other))

        val all = dao.observeAll().first().associateBy { it.id }
        assertEquals(PlantPoints.ONE, all.getValue(dry).plantPoints)
        assertEquals(PlantPoints.ONE, all.getValue(cooked).plantPoints)
        assertEquals(PlantPoints.ZERO, all.getValue(other).plantPoints)
        assertEquals(listOf(false, true, true), listOf(dry, cooked, other).map { all.getValue(it).reviewed })
    }
}
