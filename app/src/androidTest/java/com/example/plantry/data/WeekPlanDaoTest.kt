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
class WeekPlanDaoTest {

    private lateinit var db: PlantryDatabase
    private lateinit var dao: WeekPlanDao

    private val saturday = LocalDate.of(2026, 10, 3)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PlantryDatabase::class.java,
        ).build()
        dao = db.weekPlanDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertRecipe(title: String) = db.recipeDao().insert(
        Recipe(title = title, source = "", page = null, bookServings = 2, ourServings = 2, cookingTimeMinutes = 20),
    )

    @Test
    fun observeWeek_onlyThatWeekInSlotOrderWithTitles() = runTest {
        val dal = insertRecipe("Dal")
        val chili = insertRecipe("Chili")
        dao.upsert(WeekPlanSlot(saturday, 3, dal))
        dao.upsert(WeekPlanSlot(saturday, 1, chili))
        dao.upsert(WeekPlanSlot(saturday.minusWeeks(1), 0, dal))

        val week = dao.observeWeek(saturday).first()

        assertEquals(listOf(1 to "Chili", 3 to "Dal"), week.map { it.slot.position to it.recipeTitle })
    }

    @Test
    fun upsert_replacesSlot() = runTest {
        val dal = insertRecipe("Dal")
        val chili = insertRecipe("Chili")
        dao.upsert(WeekPlanSlot(saturday, 0, dal, done = true))

        dao.upsert(WeekPlanSlot(saturday, 0, chili))

        assertEquals(listOf(WeekPlanSlot(saturday, 0, chili)), dao.getSlots(saturday))
    }

    @Test
    fun setDoneAndDelete() = runTest {
        val dal = insertRecipe("Dal")
        dao.insertAll(listOf(WeekPlanSlot(saturday, 0, dal), WeekPlanSlot(saturday, 1, dal)))

        dao.setDone(saturday, 0, true)
        dao.delete(saturday, 1)

        assertEquals(listOf(WeekPlanSlot(saturday, 0, dal, done = true)), dao.getSlots(saturday))
    }

    @Test
    fun deletingRecipe_emptiesItsSlot() = runTest {
        val dal = insertRecipe("Dal")
        dao.upsert(WeekPlanSlot(saturday, 0, dal))

        db.recipeDao().deleteById(dal)

        assertEquals(emptyList<WeekPlanSlot>(), dao.getSlots(saturday))
    }
}
