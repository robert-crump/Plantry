package com.example.plantry.data

import android.database.sqlite.SQLiteConstraintException
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
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PlannedRecipeDaoTest {

    private lateinit var db: PlantryDatabase
    private lateinit var dao: PlannedRecipeDao

    private val today = LocalDate.of(2026, 10, 4)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PlantryDatabase::class.java,
        ).build()
        dao = db.plannedRecipeDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertRecipe(title: String) = db.recipeDao().insert(
        Recipe(title = title, source = "", page = null, bookServings = 2, ourServings = 2, cookingTimeMinutes = 20),
    )

    @Test
    fun observeAll_oldestPlanFirst() = runTest {
        val dal = insertRecipe("Dal")
        val chili = insertRecipe("Chili")
        dao.upsert(PlannedRecipe(dal, today))
        dao.upsert(PlannedRecipe(chili, today.minusDays(2)))

        assertEquals(listOf(chili, dal), dao.observeAll().first().map { it.recipeId })
    }

    @Test
    fun upsert_sameRecipe_replacesTheDate() = runTest {
        val dal = insertRecipe("Dal")
        dao.upsert(PlannedRecipe(dal, today.minusDays(10)))

        dao.upsert(PlannedRecipe(dal, today))

        assertEquals(listOf(PlannedRecipe(dal, today)), dao.observeAll().first())
        assertEquals(PlannedRecipe(dal, today), dao.get(dal))
    }

    @Test
    fun deletingRecipe_removesItFromGeplant() = runTest {
        val dal = insertRecipe("Dal")
        val chili = insertRecipe("Chili")
        dao.upsert(PlannedRecipe(dal, today))
        dao.upsert(PlannedRecipe(chili, today))

        db.recipeDao().deleteById(dal)

        assertNull(dao.get(dal))
        assertEquals(listOf(chili), dao.observeAll().first().map { it.recipeId })
    }

    @Test(expected = SQLiteConstraintException::class)
    fun upsert_unknownRecipe_isRejected() = runTest {
        dao.upsert(PlannedRecipe(404, today))
    }
}
