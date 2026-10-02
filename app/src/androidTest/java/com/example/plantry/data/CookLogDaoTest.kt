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

    private suspend fun insertRecipe(title: String) = db.recipeDao().insert(
        Recipe(title = title, source = "", page = null, bookServings = 2, ourServings = 2, cookingTimeMinutes = 20),
    )

    @Test
    fun observeHistory_newestFirstWithRecipeTitles() = runTest {
        val dal = insertRecipe("Dal")
        val chili = insertRecipe("Chili")
        dao.insert(CookLog(recipeId = dal, cookedOn = today.minusDays(10)))
        dao.insert(CookLog(recipeId = chili, cookedOn = today))
        dao.insert(CookLog(recipeId = dal, cookedOn = today.minusDays(1)))
        dao.insert(CookLog(recipeId = dal, cookedOn = today))

        val history = dao.observeHistory().first()

        assertEquals(
            listOf("Dal" to today, "Chili" to today, "Dal" to today.minusDays(1), "Dal" to today.minusDays(10)),
            history.map { it.recipeTitle to it.log.cookedOn },
        )
    }

    @Test
    fun observeDates_onlyThatRecipe() = runTest {
        val dal = insertRecipe("Dal")
        val chili = insertRecipe("Chili")
        dao.insert(CookLog(recipeId = dal, cookedOn = today))
        dao.insert(CookLog(recipeId = chili, cookedOn = today.minusDays(3)))

        assertEquals(listOf(today.minusDays(3)), dao.observeDates(chili).first())
    }

    @Test
    fun deleteById_removesEntry() = runTest {
        val dal = insertRecipe("Dal")
        val id = dao.insert(CookLog(recipeId = dal, cookedOn = today))

        dao.deleteById(id)

        assertEquals(emptyList<CookLogEntry>(), dao.observeHistory().first())
    }

    @Test
    fun deletingRecipe_cascadesToLog() = runTest {
        val dal = insertRecipe("Dal")
        dao.insert(CookLog(recipeId = dal, cookedOn = today))

        db.recipeDao().deleteById(dal)

        assertEquals(emptyList<CookLogEntry>(), dao.observeHistory().first())
    }
}
