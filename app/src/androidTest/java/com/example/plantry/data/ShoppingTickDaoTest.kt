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
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ShoppingTickDaoTest {

    private lateinit var db: PlantryDatabase
    private lateinit var dao: ShoppingTickDao

    private val saturday = LocalDate.of(2026, 10, 3)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PlantryDatabase::class.java,
        ).build()
        dao = db.shoppingTickDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertIngredient(name: String) = db.ingredientDao().insert(
        Ingredient(
            name = name,
            fdcId = null,
            usdaDescription = null,
            nutrition = Nutrition(),
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
    fun ticks_perWeek_tickingTwiceKeepsOne_untickRemoves() = runTest {
        val rice = insertIngredient("Reis")
        val feta = insertIngredient("Feta")
        dao.insert(ShoppingTick(saturday, rice))
        dao.insert(ShoppingTick(saturday, rice))
        dao.insert(ShoppingTick(saturday, feta))
        dao.insert(ShoppingTick(saturday.minusWeeks(1), feta))

        dao.delete(saturday, feta)

        assertEquals(listOf(rice), dao.observeTicked(saturday).first())
        assertEquals(listOf(feta), dao.observeTicked(saturday.minusWeeks(1)).first())
    }
}
