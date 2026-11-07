package com.example.plantry.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** [MealPlan]'s actions against a real database, so a failure halfway rolls the whole action back. */
@RunWith(AndroidJUnit4::class)
class MealPlanTransactionTest {

    private lateinit var db: PlantryDatabase
    private lateinit var mealPlan: MealPlan

    /** Set to make taking a recipe off Geplant fail, after its log entry was written. */
    private var failDelete = false

    private val today = LocalDate.of(2026, 10, 4)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PlantryDatabase::class.java,
        ).build()
        val planned = db.plannedRecipeDao()
        val failing = object : PlannedRecipeDao by planned {
            override suspend fun delete(recipeId: Long) {
                if (failDelete) throw IllegalStateException("delete failed")
                planned.delete(recipeId)
            }
        }
        mealPlan = MealPlan(
            failing,
            db.cookLogDao(),
            snapshot = { RecipeSnapshot.load(it, db.recipeDao(), db.ingredientDao()) },
            Transaction { db.withTransaction(it) },
        ) { today }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertRecipe(title: String) = db.recipeDao().insert(
        Recipe(title = title, source = "", page = null, servings = 2, totalTimeMinutes = 20),
    )

    @Test
    fun cook_failingHalfway_keepsNeitherTheLogEntryNorTheChangeToGeplant() = runTest {
        val dal = insertRecipe("Dal")
        mealPlan.plan(dal, today)
        failDelete = true

        try {
            mealPlan.cook(dal, today)
            fail("cook should have failed")
        } catch (_: IllegalStateException) {
        }

        assertEquals(emptyList<CookLog>(), db.cookLogDao().observeHistory().first())
        assertEquals(listOf(PlannedRecipe(dal, today)), mealPlan.observe().first())
    }

    @Test
    fun doneSeveral_failingOnTheSecond_logsNoneOfThem() = runTest {
        val dal = insertRecipe("Dal")
        val chili = insertRecipe("Chili")
        mealPlan.plan(dal, today)
        mealPlan.plan(chili, today)
        val planned = mealPlan.observe().first()
        failDelete = true

        try {
            mealPlan.done(planned)
            fail("done should have failed")
        } catch (_: IllegalStateException) {
        }

        assertEquals(emptyList<CookLog>(), db.cookLogDao().observeHistory().first())
        assertEquals(2, mealPlan.observe().first().size)
    }
}
