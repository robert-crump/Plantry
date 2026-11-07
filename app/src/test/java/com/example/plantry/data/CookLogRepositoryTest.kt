package com.example.plantry.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CookLogRepositoryTest {

    private val dao = FakeCookLogDao()
    private val repository = CookLogRepository(dao)
    private var today = LocalDate.of(2026, 10, 2)

    private suspend fun log(recipeId: Long, date: LocalDate) =
        dao.insert(CookLog(recipeId = recipeId, cookedOn = date, title = "Rezept $recipeId", stats = RecipeStats(1.0, 20.0, 40.0)))

    @Test
    fun observeStats_countsThatRecipeOnly() = runTest {
        log(recipeId = 1, date = today.minusDays(4))
        log(recipeId = 1, date = today.minusDays(1))
        log(recipeId = 2, date = today)

        assertEquals(CookingStats(2, 1), repository.observeStats(1) { today }.first())
        assertEquals(CookingStats(0, null), repository.observeStats(3) { today }.first())
    }

    @Test
    fun observeStats_readsTodayOnEveryEmission() = runTest {
        log(recipeId = 1, date = today)
        val stats = repository.observeStats(1) { today }

        today = today.plusDays(2)

        assertEquals(2L, stats.first().daysSinceLastCooked)
    }

    @Test
    fun observeLastCooked_latestDayPerRecipe() = runTest {
        log(recipeId = 1, date = today.minusDays(4))
        log(recipeId = 1, date = today.minusDays(1))
        log(recipeId = 2, date = today)

        assertEquals(mapOf(1L to today.minusDays(1), 2L to today), repository.observeLastCooked().first())
    }
}

/** In-memory [CookLogDao]; history ordering is covered by the instrumented CookLogDaoTest. */
internal class FakeCookLogDao : CookLogDao {
    val logs = MutableStateFlow<List<CookLog>>(emptyList())
    val plants = MutableStateFlow<List<CookLogPlant>>(emptyList())
    private var nextId = 1L
    private var nextPlantId = 1L

    override suspend fun insert(log: CookLog): Long {
        val id = if (log.id == 0L) nextId++ else log.id
        logs.value += log.copy(id = id)
        return id
    }

    override suspend fun insertPlants(plants: List<CookLogPlant>) {
        this.plants.value += plants.map { it.copy(id = nextPlantId++) }
    }

    /** Also deletes its plants, as the foreign key does. */
    override suspend fun deleteById(id: Long) {
        logs.value = logs.value.filter { it.id != id }
        plants.value = plants.value.filter { it.cookLogId != id }
    }

    override fun observePlants(): Flow<List<CookLogPlant>> = plants

    override suspend fun getPlants(): List<CookLogPlant> = plants.value

    override fun observeHistory(): Flow<List<CookLog>> = logs

    override fun observeDates(recipeId: Long): Flow<List<LocalDate>> =
        logs.map { all -> all.filter { it.recipeId == recipeId }.map { it.cookedOn } }

    override suspend fun getLastCooked(): List<LastCooked> = lastCooked(logs.value)

    override fun observeLastCooked(): Flow<List<LastCooked>> = logs.map(::lastCooked)

    private fun lastCooked(all: List<CookLog>) = all.filter { it.recipeId != null }
        .groupBy { it.recipeId!! }
        .map { (id, entries) -> LastCooked(id, entries.maxOf { it.cookedOn }) }
}
