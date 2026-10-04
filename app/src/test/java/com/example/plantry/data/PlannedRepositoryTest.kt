package com.example.plantry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlannedRepositoryTest {

    private val dao = FakePlannedRecipeDao()
    private val logDao = FakeCookLogDao()

    /** Recipes 1 to 3 exist. */
    private val cookLog = CookLogRepository(logDao) { id ->
        if (id in 1L..3L) RecipeSnapshot("Rezept $id", RecipeStats(1.0, 20.0, 40.0)) else null
    }
    private var today = LocalDate.of(2026, 10, 4)
    private val repository = PlannedRepository(dao, cookLog) { today }

    @Test
    fun plan_addsTheRecipeOnceWithToday() = runTest {
        repository.plan(1)
        repository.plan(1)

        assertEquals(listOf(PlannedRecipe(1, today)), repository.observe().first())
        assertTrue(repository.observeIsPlanned(1).first())
        assertFalse(repository.observeIsPlanned(2).first())
    }

    @Test
    fun observe_dropsEntriesPlannedMoreThanSevenDaysAgo() = runTest {
        dao.upsert(PlannedRecipe(1, today.minusDays(7)))
        dao.upsert(PlannedRecipe(2, today.minusDays(8)))
        val planned = repository.observe()

        assertEquals(listOf(1L), planned.first().map { it.recipeId })

        // Read on every emission: a day later the first one is gone too.
        today = today.plusDays(1)
        assertEquals(emptyList<PlannedRecipe>(), planned.first())
    }

    @Test
    fun plan_droppedOffRecipe_comesBackWithToday() = runTest {
        dao.upsert(PlannedRecipe(1, today.minusDays(10)))
        assertFalse(repository.observeIsPlanned(1).first())

        repository.plan(1)

        assertEquals(listOf(PlannedRecipe(1, today)), repository.observe().first())
    }

    @Test
    fun cook_logsWithSnapshotAndTakesItOffGeplant() = runTest {
        repository.plan(1)
        repository.plan(2)
        today = today.plusDays(1)

        val cooked = repository.cook(1, today.minusDays(1))!!

        assertEquals(PlannedRecipe(1, today.minusDays(1)), cooked.unplanned)
        assertEquals(listOf(2L), repository.observe().first().map { it.recipeId })
        assertEquals(
            listOf(CookLog(cooked.logId, 1, today.minusDays(1), "Rezept 1", RecipeStats(1.0, 20.0, 40.0))),
            logDao.logs.value,
        )
    }

    @Test
    fun undoCook_restoresLogAndGeplant() = runTest {
        repository.plan(1)
        val cooked = repository.cook(1, today)!!

        repository.undoCook(cooked)

        assertEquals(listOf(PlannedRecipe(1, today)), repository.observe().first())
        assertEquals(emptyList<CookLog>(), logDao.logs.value)
    }

    @Test
    fun cook_recipeNotPlanned_onlyLogs() = runTest {
        val cooked = repository.cook(3, today)!!

        assertNull(cooked.unplanned)
        repository.undoCook(cooked)
        assertEquals(emptyList<PlannedRecipe>(), repository.observe().first())
        assertEquals(emptyList<CookLog>(), logDao.logs.value)
    }

    @Test
    fun cook_droppedOffEntry_isClearedButNotRestoredByUndo() = runTest {
        dao.upsert(PlannedRecipe(1, today.minusDays(9)))

        val cooked = repository.cook(1, today)!!

        assertNull(cooked.unplanned)
        assertEquals(emptyList<PlannedRecipe>(), dao.all.value)
    }

    @Test
    fun cook_missingRecipe_changesNothing() = runTest {
        dao.upsert(PlannedRecipe(99, today))

        assertNull(repository.cook(99, today))

        assertEquals(listOf(PlannedRecipe(99, today)), dao.all.value)
        assertEquals(emptyList<CookLog>(), logDao.logs.value)
    }

    @Test
    fun removeAndRestore_keepTheOriginalDate() = runTest {
        repository.plan(2)
        today = today.plusDays(2)

        val removed = repository.remove(2)!!
        assertEquals(emptyList<PlannedRecipe>(), repository.observe().first())
        repository.restore(removed)

        assertEquals(listOf(PlannedRecipe(2, today.minusDays(2))), repository.observe().first())
        assertNull(repository.remove(3))
    }

    @Test
    fun plannedItems_currentStatsInPlanOrder_missingRecipesSkipped() {
        val recipes = listOf(
            Recipe(1, "Dal", "", null, 2, 2, null),
            Recipe(2, "Curry", "", null, 4, 2, null),
        )
        val lentils = Ingredient(
            id = 5, name = "Linsen", fdcId = null, usdaDescription = null,
            nutrition = Nutrition(kcal = 350.0, protein = 25.0, carbs = 50.0, sugar = 2.0, fat = 1.0, fibre = 10.0),
            unitWeights = emptyList(), buyUnit = BuyUnit.GRAMS, packSizeGrams = null,
            storeSection = StoreSection.OTHER, staple = false, plantPoints = PlantPoints.ONE,
            buyAsIngredientId = null, buyAsYieldFactor = null, reviewed = true,
        )
        val lines = listOf(RecipeIngredient(1, 1, 0, "200 g Linsen", 200.0, 5))
        val planned = listOf(PlannedRecipe(2, today.minusDays(1)), PlannedRecipe(9, today), PlannedRecipe(1, today))

        val items = PlannedItem.of(planned, recipes, lines, mapOf(5L to lentils))

        assertEquals(
            listOf(
                PlannedItem(planned[0], RecipeSnapshot("Curry", RecipeStats(0.0, 0.0, 0.0))),
                PlannedItem(planned[2], RecipeSnapshot("Dal", RecipeStats(1.0, 25.0, 50.0))),
            ),
            items,
        )
    }
}

/** In-memory [PlannedRecipeDao]; ordering and the foreign key are covered by the instrumented DAO test. */
private class FakePlannedRecipeDao : PlannedRecipeDao {
    val all = MutableStateFlow<List<PlannedRecipe>>(emptyList())

    override suspend fun upsert(planned: PlannedRecipe) {
        all.value = all.value.filter { it.recipeId != planned.recipeId } + planned
    }

    override suspend fun get(recipeId: Long) = all.value.firstOrNull { it.recipeId == recipeId }

    override suspend fun delete(recipeId: Long) {
        all.value = all.value.filter { it.recipeId != recipeId }
    }

    override fun observeAll(): Flow<List<PlannedRecipe>> = all
}
