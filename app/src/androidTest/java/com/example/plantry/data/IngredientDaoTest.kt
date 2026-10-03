package com.example.plantry.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun ingredient(name: String, buyAs: Long? = null) = Ingredient(
        name = name,
        fdcId = 168877,
        usdaDescription = "Rice, white, long-grain, raw",
        nutrition = Nutrition(365.0, 7.13, 79.95, 0.12, 0.66, 1.3),
        unitWeights = listOf(UnitWeight("cup", 185.0), UnitWeight("tbsp", 11.6)),
        buyUnit = BuyUnit.PACK,
        packSizeGrams = 1000.0,
        storeSection = StoreSection.DRY_GOODS,
        staple = false,
        plantPoints = PlantPoints.ZERO,
        buyAsIngredientId = buyAs,
        buyAsYieldFactor = buyAs?.let { 0.4 },
        reviewed = true,
    )

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
    fun getBuyAsLinks_returnsEveryLink() = runTest {
        val dry = dao.insert(ingredient("Reis, trocken"))
        val cooked = dao.insert(ingredient("Reis, gekocht", buyAs = dry))

        assertEquals(
            setOf(BuyAsLink(dry, null), BuyAsLink(cooked, dry)),
            dao.getBuyAsLinks().toSet(),
        )
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
