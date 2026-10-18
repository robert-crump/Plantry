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
class CookLogDaoTest {

    private lateinit var db: PlantryDatabase
    private lateinit var dao: CookLogDao

    private val today = LocalDate.of(2026, 10, 2)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PlantryDatabase::class.java,
        ).build()
        dao = db.cookLogDao()
    }

    @After
    fun tearDown() = db.close()

    private val titles = mutableMapOf<Long, String>()

    private suspend fun insertRecipe(title: String) = db.recipeDao().insert(
        Recipe(title = title, source = "", page = null, servings = 2, totalTimeMinutes = 20),
    ).also { titles[it] = title }

    private suspend fun insertIngredient(name: String) = db.ingredientDao().insert(
        Ingredient(name = name, fdcId = null, usdaDescription = null, nutrition = Nutrition(), storeSection = StoreSection.OTHER, plantPoints = PlantPoints.ONE),
    )

    private fun log(recipeId: Long, cookedOn: LocalDate) =
        CookLog(recipeId = recipeId, cookedOn = cookedOn, title = titles.getValue(recipeId), stats = RecipeStats(1.0, 20.0, 40.0))

    @Test
    fun observeHistory_newestFirstWithSnapshotTitles() = runTest {
        val dal = insertRecipe("Dal")
        val chili = insertRecipe("Chili")
        dao.insert(log(dal, today.minusDays(10)))
        dao.insert(log(chili, today))
        dao.insert(log(dal, today.minusDays(1)))
        dao.insert(log(dal, today))

        val history = dao.observeHistory().first()

        assertEquals(
            listOf("Dal" to today, "Chili" to today, "Dal" to today.minusDays(1), "Dal" to today.minusDays(10)),
            history.map { it.title to it.cookedOn },
        )
    }

    @Test
    fun observeDates_onlyThatRecipe() = runTest {
        val dal = insertRecipe("Dal")
        val chili = insertRecipe("Chili")
        dao.insert(log(dal, today))
        dao.insert(log(chili, today.minusDays(3)))

        assertEquals(listOf(today.minusDays(3)), dao.observeDates(chili).first())
    }

    @Test
    fun getAndObserveLastCooked_latestDatePerCookedRecipe() = runTest {
        val dal = insertRecipe("Dal")
        val chili = insertRecipe("Chili")
        insertRecipe("Nie gekocht")
        dao.insert(log(dal, today))
        dao.insert(log(dal, today.minusDays(30)))
        dao.insert(log(chili, today.minusDays(3)))

        assertEquals(
            setOf(LastCooked(dal, today), LastCooked(chili, today.minusDays(3))),
            dao.getLastCooked().toSet(),
        )
        assertEquals(dao.getLastCooked().toSet(), dao.observeLastCooked().first().toSet())
    }

    @Test
    fun deleteById_removesEntry() = runTest {
        val dal = insertRecipe("Dal")
        val id = dao.insert(log(dal, today))

        dao.deleteById(id)

        assertEquals(emptyList<CookLog>(), dao.observeHistory().first())
    }

    @Test
    fun insertWithPlants_storesThemForTheEntry_andDeleteTakesThemAlong() = runTest {
        val dal = insertRecipe("Dal")
        val lentils = insertIngredient("Linsen")
        val plants = listOf(CookLogPlant(cookLogId = 99, ingredientId = lentils, name = "Linsen", plantPoints = 1.0))
        val first = dao.insert(log(dal, today.minusDays(1)), plants)
        val second = dao.insert(log(dal, today), plants)

        assertEquals(listOf(first, second), dao.getPlants().map { it.cookLogId })

        dao.deleteById(first)

        assertEquals(listOf(second), dao.observePlants().first().map { it.cookLogId })
    }

    @Test
    fun deletingIngredient_keepsItsPlantsWithNameAndPoints() = runTest {
        val dal = insertRecipe("Dal")
        val cumin = insertIngredient("Kreuzkümmel")
        val id = dao.insert(log(dal, today), listOf(CookLogPlant(ingredientId = cumin, name = "Kreuzkümmel", plantPoints = 0.25)))

        db.ingredientDao().deleteById(cumin)
        db.recipeDao().deleteById(dal)

        assertEquals(
            listOf(CookLogPlant(cookLogId = id, ingredientId = null, name = "Kreuzkümmel", plantPoints = 0.25)),
            dao.getPlants().map { it.copy(id = 0) },
        )
    }

    @Test
    fun deletingRecipe_keepsLogWithoutRecipe() = runTest {
        val dal = insertRecipe("Dal")
        val chili = insertRecipe("Chili")
        val id = dao.insert(log(dal, today))
        dao.insert(log(chili, today.minusDays(2)))

        db.recipeDao().deleteById(dal)

        val history = dao.observeHistory().first()
        assertEquals(log(dal, today).copy(id = id, recipeId = null), history.first())
        assertEquals(listOf(LastCooked(chili, today.minusDays(2))), dao.getLastCooked())
    }
}
